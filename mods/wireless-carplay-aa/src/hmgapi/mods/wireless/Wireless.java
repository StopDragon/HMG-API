package hmgapi.mods.wireless;
// Modified by Korea Projection (StopDragon/HMG-API, branch korea-build), 2026-11-08 (mod 2.3.0):
//   - the car navigation's lane frames (updateLaneInfo) are skipped too while a phone guides, so
//     the cluster and HUD show the phone's lanes only (1.7.1 map 2-H); back as soon as it stops
//   - with Korea Projection's debug log on, what the car navigation sends (icon, lane frame, 2C)
//     is logged as numbers (StockLog); Android Auto's turn log lines (AaTurns) go to the mod's log
// Korea Projection 1.7.2 수정(2026-11-08, 모드 2.3.1 판 그대로, 계획 M-13): 디버그일 때 순정 관찰 기록을 넓혔다.
//   교차로 이름은 객체에서 꺼내 글자 수만 남긴다. updateCluDataNew_14·updateRoadEventInfo는 지도(훅 지점)에
//   없는 메서드라 디버그일 때만 Hooks로 직접 관찰만 건다(막거나 바꾸지 않음, 없으면 한 줄 남기고 넘어감).
// 1.7.2 리뷰 반영: 순정 계기판 호출(NAVI_TBT)의 마지막 값을 늘 기억했다가, 폰 안내가 끝나면 순정 호출 훅 안에서
//   1.5초·5초 뒤에 다시 보낸다(StockReplay, 순정 호출이 없으면 백업 타이머 한 번). 우리 지우기가 순정 값보다 늦게
//   도착하거나 순정이 값을 바꾸지 않아도 계기판이 빈 채로 남지 않게 한다.
// 1.7.2 차-5 뒤 진단: 디버그 로그가 켜졌으면 DiPO 쪽(Dipo.debug → CpTurns)에 알려 곧 회전 판단 진단 줄을 남기게 한다.
// Korea Projection 1.7.3(2026-11-10, 모드 2.4.1, 계획 A-1): GPM에서 PP가 뜬 뒤(runningPP) 버려진 무선 AA 준비(1)를
//   한 번 더 보낸다(AaReady). 설정 aa_ready_resend(기본 켬), 패키지 신호 sys.kp.aa_ap=1일 때만.
// Korea Projection 1.7.4(2026-11-12, 모드 2.4.2, "순정과 똑같이"): 순정 내비 단속 정보 프레임의 막대 칸 바꾸기(TrapBar)와
//   설정 aa_name_hold를 뺐다. 설정 파일에 옛 aa_name_hold 값이 남아 있어도 읽지 않는다(HMG-API는 모르는 열쇠를 그냥 둔다).

import android.content.ComponentName;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;

import hmgapi.hook.Car;
import hmgapi.hook.Hooks;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;
import hmgapi.hook.ModEntry;
import hmgapi.hook.Points;

/**
 * The Wireless mod's hooks (the rest of the mod is file changes in patches.json).
 *
 * Phone Projection menu (setting "menu"): with the wireless CarPlay/AA features
 * on, HKMC_BtSettings opens the wireless projection page for Settings > Phone
 * Projection (no split screen, and only phones the stock wireless service paired).
 * Stock US/Canada opens Device Connections. On US/Canada units the hook on the
 * "open the wireless page" call does what the non-wireless branch does instead.
 * Through HMG-API's hook points (btsettings.projection_helper.*): HMG-API's map for
 * this car says what they are in this build.
 *
 * USB drives (setting "usb_drives"): the Android Auto app (HKMC_GPM) starts a wired
 * session for every USB_DEVICE_ATTACHED, so a USB drive (install stick, music) leaves
 * GalConnectionManager "Already AA Starting" with a wired start that never connects, and
 * wireless Android Auto is refused until the head unit restarts. The hook on
 * Context.startService in that app drops a wired start (startMode 1) for a device whose
 * interfaces are all mass storage (8) or hub (9). Phones and devices with no interfaces
 * start as before (hook point framework.context_impl.start_service).
 */
public final class Wireless extends ModEntry {
    static final String BTSETTINGS = "com.android.btsettings";
    static final String GPM = "com.google.android.projection.sink";
    /**
     * Q-1(1.7.4): Wi-Fi 장치 이름(패키지 hotspot.sh·status.sh의 WIF 기본값과 같음). 1.8.0 USB 동글 준비로 한 곳에 모은다.
     * DiPO 앱 파트(dipo)는 클래스 로더가 달라 ApCheck.WIFI_IF에 같은 값을 둔다(tools/dupsame이 비교). 밖에서 바꾸는 길은 아직 없다
     */
    static final String WIFI_IF = "wlan0";
    static final String GAL_SERVICE = "com.google.android.projection.service.GalReceiverService";
    static final int USB_CLASS_MASS_STORAGE = 8, USB_CLASS_HUB = 9;
    static final String HELPER = "btsettings.projection_helper";

    static final String NAVIGATION = "com.mnsoft.navi";
    static final String DIPO = "com.daudio.app.dipo";
    static final String IAP2 = "com.tcc.iap2server";
    /** from DiPO's CarPlay Bluetooth bridge: start / stop an iAP2 session over Bluetooth */
    static final String BT_ATTACHED = "com.ev6.cpwl.BT_ATTACHED", BT_DETACHED = "com.ev6.cpwl.BT_DETACHED";
    static final String USB_CARPLAY = "android.hardware.usb.action.CARPLAY_DEVICE_ATTACHED";
    static final String USB_DIGITAL_AUDIO = "android.hardware.usb.action.DIGITAL_AUDIO_DEVICE_ATTACHED";
    /** the iAP2 component of the Bluetooth session we asked for (0: none) */
    static volatile int btComponent;
    /** NavigationCluster's turn-by-turn calls: the car navigation's own guidance, off while a phone guides */
    static final String[] NAVI_TBT = { "update_tbt_info", "update_tbt_sub_info", "update_tbt_sub_info_b", "update_tbt_sub_info2",
        "update_tbt_sub_info2_b", "update_tbt_sub_info_2c", "update_remain_dis", "update_tp_crossroad_name",
        "update_tp_destination_name", "update_tp_turn_list", "update_tp_turn_list_new", "update_tp_lane_info",
        "update_lane_info" };
    /** Korea Projection's debug log is on (StockLog.DEBUG_FILE, read as root in the zygote; false in a late load) */
    static volatile boolean debug;
    /** Android Auto turn log lines so far in this process (AaTurns.TURN_LOGS_MAX in all) */
    static final java.util.concurrent.atomic.AtomicInteger aaTurnLogs = new java.util.concurrent.atomic.AtomicInteger();

    @Override
    public void zygoteInit(ModContext mod) {
        try {
            debug = new java.io.File(StockLog.DEBUG_FILE).exists();
        } catch (Throwable t) {
            debug = false;
        }
    }

    @Override
    public void appLoaded(final ModContext mod, LoadedApp app) {
        if (NAVIGATION.equals(app.packageName)) navigation(mod, app);
        if (IAP2.equals(app.packageName)) iap2(mod, app);
        if (GPM.equals(app.packageName) && mod.settingBoolean("usb_drives", true)) usbDrives(mod, app);
        if (GPM.equals(app.packageName) && mod.settingBoolean("tbt", true)) gpmTurnByTurn(mod, app);
        // Korea Projection (korea-build): the menu fix is for US/Canada only, and its hook point isn't on every
        // Korean firmware (NQ5 260604: no btsettings.projection_helper.*). A declared point a car lacks turns off
        // all of this mod's hooks there (HookPlan), so the Korean build doesn't declare it and never hooks it.
        if (BTSETTINGS.equals(app.packageName) && mod.settingBoolean("menu", true) && MENU_DECLARED) phoneProjectionMenu(mod, app);
        // 한국 차 빌드: 패키지가 띄운 AP를 무선 안드로이드 오토가 쓰게 한다(AaWifi, 켜기 속성이 있을 때만 동작)
        if ((GPM.equals(app.packageName) || AaWifi.PHONEPROJ.equals(app.packageName)) && mod.settingBoolean("aa_ap", true)) AaWifi.hook(mod, app);
        // 한국 차 빌드 1.7.3(A-1): PP가 늦게 떠서 버려진 무선 AA 준비(1)를 PP가 뜬 뒤 다시 보낸다
        if (GPM.equals(app.packageName)) aaReady(mod, app, mod.settingBoolean("aa_ready_resend", true));
        if (DIPO.equals(app.packageName) && (mod.settingBoolean("tbt", true) || mod.settingBoolean("bridge", true) || mod.settingBoolean("audio", true))) dipo(mod, app);
    }

    /**
     * DiPO (CarPlay): turn-by-turn, the Bluetooth bridge and Wi-Fi audio, in the mod's DiPO part
     * (hmgapi.mods.wireless.dipo.Dipo), which links against DiPO's iAP2 and Bluetooth
     * libraries.
     */
    private void dipo(ModContext mod, LoadedApp app) {
        try {
            Class<?> part = mod.appPart(app, "hmgapi.mods.wireless.dipo.Dipo");
            // 1.7.2 진단: 디버그 로그가 켜졌으면 DiPO 쪽(CpTurns)도 곧 회전 판단 줄을 남긴다(숫자만)
            try {
                part.getField("debug").setBoolean(null, debug);
            } catch (Throwable t) {
                mod.log("DiPO part: no debug flag (" + t.getClass().getSimpleName() + ")");
            }
            part.getMethod("start", ModContext.class, LoadedApp.class).invoke(null, mod, app);
        } catch (Throwable t) {
            mod.error("DiPO part", t instanceof java.lang.reflect.InvocationTargetException ? t.getCause() : t);
        }
    }

    /**
     * In the car navigation app, while a phone guides (Android Auto or CarPlay): its own
     * turn-by-turn calls to the cluster and HUD are skipped, so they don't fight the
     * phone's; lane frames too (Korea Projection 2.3.0: the stock navigation sends them even
     * with no route, and they mixed with the phone's). Its speed limit, cameras, traps, warning
     * distances and driver-assist data still go through (phones carry none of that). Every call
     * goes through again once the phone stops guiding. Framework calls only: no app names.
     * With the debug log on, the icon, lane frame and 2C numbers it sends are logged (StockLog),
     * skipped or not.
     */
    private void navigation(final ModContext mod, final LoadedApp app) {
        if (!mod.settingBoolean("tbt", true)) return;
        final StockLog seen = new StockLog(debug);
        final StockReplay replay = new StockReplay();
        for (final String m : NAVI_TBT) {
            mod.hook(null, "framework.navigation_cluster." + m, new MethodHook() {
                @Override
                protected void beforeHookedMethod(Param p) {
                    if (REPLAYING.get() != null) return;   // 우리가 다시 보내는 순정 값: 그대로 통과
                    boolean phone = Car.phoneGuiding(app.context);
                    String line = seen.line(m, debug ? stockArgs(m, p.args) : p.args, phone);
                    if (line != null) mod.log(line);
                    // 막든 통과하든 최신 순정 값을 기억하고, 폰 안내가 끝났으면 이 호출 자리에서 다시 보낸다(2차 리뷰 1)
                    StockReplay.Out o = replay.call(m, p.thisObject, p.method, p.args, phone, android.os.SystemClock.elapsedRealtime());
                    if (phone) p.setResult(null);
                    resend(mod, o);
                    if (o.backupGen != 0) scheduleBackup(mod, app, replay, o.backupGen);
                }
            });
        }
        if (debug) observeOnly(mod, seen, StockLog.CLU_DATA_14, "updateCluDataNew_14", int.class, int.class, int.class, int.class, int.class);
        if (debug) observeOnly(mod, seen, StockLog.ROAD_EVENT, "updateRoadEventInfo", byte[].class);
        mod.log("navigation: turn-by-turn and lanes yield to the phone in " + app.processName
                + (debug ? " (debug: stock values logged)" : ""));
    }

    /** 로그용 기기 주소 가리기(모두 가림, Mac.mask) */
    static String mask(String a) {
        return Mac.mask(a);
    }

    /** 다시 보내는 중인 스레드 표시: 이 호출은 기록·차단 없이 통과시킨다 */
    static final ThreadLocal<Boolean> REPLAYING = new ThreadLocal<Boolean>();
    private static android.os.Handler replayHandler;

    static synchronized android.os.Handler replayHandler() {
        if (replayHandler == null) {
            android.os.HandlerThread t = new android.os.HandlerThread("hmgapi-navi-replay");
            t.start();
            replayHandler = new android.os.Handler(t.getLooper());
        }
        return replayHandler;
    }

    /**
     * StockReplay가 고른 순정 값을 다시 보낸다(1.7.2 리뷰 2·2차 리뷰 1). 보통 순정 호출 훅 안(순정 스레드)에서
     * 불린다. 같은 hooked 메서드를 부르되 REPLAYING 표시로 기억·막기·기록을 건너뛴다. 호출마다 예외를 잡고,
     * 로그에는 개수만 남긴다.
     */
    static void resend(ModContext mod, StockReplay.Out o) {
        if (o.resend.isEmpty()) return;
        int ok = 0, failed = 0;
        REPLAYING.set(Boolean.TRUE);
        try {
            for (Object[] c : o.resend) {
                try {
                    ((java.lang.reflect.Method) c[1]).invoke(c[0], (Object[]) c[2]);
                    ok++;
                } catch (Throwable t) {
                    failed++;
                }
            }
        } finally {
            REPLAYING.remove();
        }
        mod.log("navigation: stock cluster values resent after phone guidance, round " + o.round + ": " + ok + " calls"
                + (failed > 0 ? ", " + failed + " failed" : ""));
    }

    /**
     * 순정 호출이 오지 않을 때를 위한 백업 한 번(StockReplay.BACKUP_MS 뒤). 세대가 같고 아직 끝나지 않았으며 폰 안내가
     * 다시 시작되지 않았을 때만 보낸다. 한 번의 끝에 하나만 걸리고 반복하지 않는다.
     */
    private static void scheduleBackup(final ModContext mod, final LoadedApp app, final StockReplay replay, final int gen) {
        replayHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (Car.phoneGuiding(app.context)) return;
                    resend(mod, replay.backup(gen));
                } catch (Throwable t) {
                    mod.error("navigation: stock cluster resend", t);
                }
            }
        }, StockReplay.BACKUP_MS);
    }

    /** 관찰 기록용 인자: 교차로 이름 객체는 이름 문자열로 바꿔 넘긴다(StockLog는 글자 수만 남긴다) */
    static Object[] stockArgs(String point, Object[] args) {
        if (!"update_tp_crossroad_name".equals(point) || args == null || args.length < 1 || args[0] == null) return args;
        try {
            Object n = Points.field(null, "framework.crossroad_name.name").get(args[0]);
            return new Object[] { n instanceof String ? n : null };
        } catch (Throwable t) {
            return new Object[0];   // 꺼내지 못하면 남기지 않는다
        }
    }

    /**
     * 순정 내비가 부르는 NavigationCluster 메서드 하나를 관찰만 한다(1.7.2, M-13, 디버그 때만). 지도에 없는 메서드라
     * HookPlan을 거치지 않고 이 프로세스에서만 건다. 없는 차에서는 한 줄 남기고 넘어간다(다른 훅에 영향 없음).
     */
    private static void observeOnly(final ModContext mod, final StockLog seen, final String label, String method, Object... types) {
        try {
            Object[] a = new Object[types.length + 1];
            System.arraycopy(types, 0, a, 0, types.length);
            a[types.length] = new MethodHook() {
                @Override
                protected void beforeHookedMethod(Param p) {
                    String line = seen.line(label, p.args, false);
                    if (line != null) mod.log(line);
                }
            };
            Hooks.findAndHookMethod(Points.cls(null, "framework.navigation_cluster"), method, a);
        } catch (Throwable t) {
            mod.log("navigation: no NavigationCluster." + method + " to observe (" + t.getClass().getSimpleName() + ")");
        }
    }

    /**
     * The iAP2 server. Turn-by-turn ("tbt"): it offers RouteGuidance to the iPhone only
     * when the car's stepofspec is 999 or more (US/Canada: 105), so HMG-API sets that one
     * flag after it decides. Bluetooth bridge ("bridge"): DiPO's bridge asks for an iAP2
     * session over Bluetooth (BT_ATTACHED / BT_DETACHED, from apps holding
     * BLUETOOTH_PRIVILEGED); a USB CarPlay attach ends it first, before the server's own
     * receiver handles that attach.
     */
    private void iap2(final ModContext mod, LoadedApp app) {
        final ClassLoader cl = app.classLoader;
        if (mod.settingBoolean("tbt", true)) {
            mod.hook(cl, "iap2.init_info.check_supports_features", new MethodHook() {
                @Override
                protected void afterHookedMethod(Param p) {
                    try {
                        Points.field(cl, "iap2.init_info.route_guidance").setInt(null, 1);
                    } catch (Exception e) {
                        mod.error("route guidance flag", e);
                    }
                }
            });
        }
        if (!mod.settingBoolean("bridge", true)) return;
        mod.hook(cl, "iap2.service.on_create", new MethodHook() {
            @Override
            protected void afterHookedMethod(Param p) {
                final Object svc = p.thisObject;
                IntentFilter f = new IntentFilter();
                f.addAction(BT_ATTACHED);
                f.addAction(BT_DETACHED);
                ((Context) svc).registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent i) {
                        try {
                            if (BT_ATTACHED.equals(i.getAction())) btAttach(mod, cl, svc, i);
                            else stopBt(mod, cl, svc, "detach");
                        } catch (Throwable t) {
                            mod.error("bridge " + i.getAction(), t);
                        }
                    }
                }, f, "android.permission.BLUETOOTH_PRIVILEGED", null);
                mod.log("bridge: iAP2 listens for DiPO's Bluetooth sessions");
            }
        });
        mod.hook(cl, "iap2.service_receiver.on_receive", new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                Intent i = (Intent) p.args[1];
                String a = i == null ? null : i.getAction();
                if (!USB_CARPLAY.equals(a) && !USB_DIGITAL_AUDIO.equals(a)) return;
                try {
                    stopBt(mod, cl, Points.get(cl, "iap2.service_receiver.service", p.thisObject), "usb attach");
                } catch (Throwable t) {
                    mod.error("bridge: usb attach", t);
                }
            }
        });
    }

    static void btAttach(ModContext mod, ClassLoader cl, Object svc, Intent i) {
        int comp = i.getIntExtra("component", 4);
        if (comp < 3 || comp > 5) comp = 4;
        String serial = i.getStringExtra("serial");
        if (btComponent == 0 && Boolean.TRUE.equals(Points.call(cl, "iap2.service.is_connected", null))) {
            mod.log("bridge: attach ignored, another iAP2 session is up (component "
                    + Points.call(cl, "iap2.service.get_component", null) + ")");
            return;
        }
        stopBt(mod, cl, svc, "re-attach");
        btComponent = comp;
        Points.call(cl, "iap2.service.set_device_serial", svc, serial);
        Points.call(cl, "iap2.service.receive_connect", svc, comp);
        mod.log("bridge: Bluetooth iAP2 session requested, component " + comp + " serial " + mask(serial));
    }

    static void stopBt(ModContext mod, ClassLoader cl, Object svc, String why) {
        int c = btComponent;
        if (c == 0) return;
        btComponent = 0;
        Points.call(cl, "iap2.service.set_device_serial", svc, (Object) null);
        Points.call(cl, "iap2.service.receive_disconnect", svc, c);
        mod.log("bridge: Bluetooth iAP2 session stopped, component " + c + " (" + why + ")");
    }

    /**
     * Android Auto turn-by-turn (GPM). GPM's own turn-by-turn manager only exists when
     * the car's stepofspec is 999 or more (US/Canada: 105), and it claims the cluster in a
     * way these cars don't give back. HMG-API leaves GPM's manager off and runs its own
     * (AaTurns) from the AA library's callbacks, sending through HMG-API's Cluster API.
     * GPM tells the phone its cluster takes pictures (IMAGE); HMG-API says ENUM, so the
     * phone sends turn data the cluster can show. One AaTurns per connection (GalReceiver),
     * all on one thread.
     */
    private void gpmTurnByTurn(final ModContext mod, final LoadedApp app) {
        final ClassLoader cl = app.classLoader;
        if (Car.stepOfSpec() >= 999) {
            mod.log("Android Auto: stepofspec is 999 or more, GPM's own turn-by-turn runs (HMG-API's stays off)");
            return;
        }
        // the AA library: protocol 1.6 and the turn-by-turn extras packed into its callbacks, in memory (file stays stock)
        mod.nativePatch(app, "libautoreceiver_infobank.so");
        mod.hook(cl, "gpm.navigation_status.init", new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                if (p.args[3] == Points.get(cl, "gpm.cluster_type.image", null)) p.args[3] = Points.get(cl, "gpm.cluster_type.enum", null);
            }
        });
        final java.util.Map<Object, AaTurns> turns = new java.util.WeakHashMap<Object, AaTurns>();
        final android.os.HandlerThread thread = new android.os.HandlerThread("hmgapi-aa-tbt");
        thread.start();
        final android.os.Handler handler = new android.os.Handler(thread.getLooper());
        abstract class OnTurns extends MethodHook {
            abstract void run(AaTurns t, Object[] a);

            /** receiver: the GalReceiver; create: make its AaTurns if there's none yet */
            void post(final Object receiver, final boolean create, final Object[] args) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AaTurns t;
                            synchronized (turns) {
                                t = turns.get(receiver);
                                if (t == null && create) {
                                    hmgapi.hook.Cluster c = hmgapi.hook.Cluster.of(app.context);
                                    if (c == null) return;
                                    turns.put(receiver, t = new AaTurns(app.context, c, handler));
                                    t.log = new AaTurns.Sink() {
                                        @Override
                                        public void line(String s) {
                                            // the 300-line cap for the whole process, not per connection
                                            if (aaTurnLogs.incrementAndGet() <= AaTurns.TURN_LOGS_MAX) mod.log(s);
                                        }
                                    };
                                    mod.log("Android Auto: turn-by-turn to the cluster for this connection");
                                }
                            }
                            if (t != null) OnTurns.this.run(t, args);
                        } catch (Throwable x) {
                            mod.error("Android Auto turn-by-turn", x);
                        }
                    }
                });
            }

            @Override
            protected void afterHookedMethod(Param p) {
                post(Points.get(cl, "gpm.navigation_status.receiver", p.thisObject), true, p.args.clone());
            }
        }
        mod.hook(cl, "gpm.navigation_status.next_turn", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                t.nextTurn((String) a[0], (Integer) a[1], (Integer) a[2], (Integer) a[4], (Integer) a[5]);
            }
        });
        mod.hook(cl, "gpm.navigation_status.next_turn_distance", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                t.nextTurnDistance((Integer) a[0], (Integer) a[1], (Integer) a[2], (Integer) a[3]);
            }
        });
        mod.hook(cl, "gpm.navigation_status.status", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                t.status((Integer) a[0]);
            }
        });
        mod.hook(cl, "gpm.receiver.send_navigation_focus", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                // 순정으로 가면 지우고 폰 이벤트를 멈춘다. 폰으로 돌아오면 다시 보낸다(1.7.2 M-14, 2차 리뷰 3)
                if (a[0].equals(Points.get(cl, "gpm.receiver.focus_native", null))) t.stop();
                else t.focusPhone();
            }

            @Override
            protected void afterHookedMethod(Param p) {
                post(p.thisObject, false, p.args.clone());
            }
        });
        mod.hook(cl, "gpm.receiver.destroy", new OnTurns() {
            @Override
            void run(AaTurns t, Object[] a) {
                t.end();
                synchronized (turns) {
                    turns.values().remove(t);
                }
            }

            @Override
            protected void beforeHookedMethod(Param p) {
                post(p.thisObject, false, new Object[0]);
            }

            @Override
            protected void afterHookedMethod(Param p) {}
        });
    }

    /** AaReady 훅이 이 프로세스에 걸렸다(한 번만) */
    private static boolean aaReadyHooked;

    /**
     * 1.7.3(A-1): GPM의 GalReceiverService$PPActionListener.runningPP() 뒤에, 패키지 신호(sys.kp.aa_ap=1, AaWifi.on)가
     * 있으면 0.5초 뒤 GPM 메인 스레드에서 한 번 AaReady.resend를 부른다(PPService의 binder 호출을 붙잡지 않게, GPM의 다른
     * 처리와 같은 스레드에서). 신호가 아직 0이면 1초마다 최대 60초 다시 보고 1이 되면 한 번 부른다(B-1, 1.7.4). 반복 타이머는 남지 않는다. 설정 aa_ready_resend가 꺼져 있으면 훅을 걸지 않는다
     * (AaReady.install). HMG-API 지도에 없는 메서드라 Hooks로 직접 건다. 없으면 한 줄 남기고 넘어간다.
     * 리뷰 반영: GalReceiverService.requestWLDeviceReadyStatusUpdate 앞에서 기기별 준비(1) 시각을 적어(AaReady.noteReady),
     * PP가 뜬 뒤 순정이 이미 보낸 기기는 다시 보내지 않는다. 이 훅을 못 걸면 전처럼 모두 보낸다.
     */
    private static synchronized void aaReady(final ModContext mod, final LoadedApp app, boolean setting) {
        if (aaReadyHooked) return;
        aaReadyHooked = true;
        try {
            AaReady.install(setting, new AaReady.Installer() {
                @Override
                public void install() {
                    aaReadyHooks(mod, app);
                }
            });
        } catch (Throwable t) {
            mod.log(AaReady.LINE + "훅 없음 (" + t.getClass().getSimpleName() + ")");
        }
    }

    /** aaReady의 두 훅(설정이 켜졌을 때만 불림) */
    private static void aaReadyHooks(final ModContext mod, final LoadedApp app) {
        final AaReady.Log log = new AaReady.Log() {
            @Override
            public void line(String s) {
                mod.log(s);
            }
        };
        try {
            Class<?> gal = Class.forName(AaReady.GAL_SERVICE, false, app.classLoader);
            Hooks.findAndHookMethod(gal, "requestWLDeviceReadyStatusUpdate", "android.phoneprojection.WLDeviceInfo", int.class, new MethodHook() {
                @Override
                protected void beforeHookedMethod(Param p) {
                    AaReady.noteReady(p.args[0], p.args[1], android.os.SystemClock.elapsedRealtime());
                }
            });
        } catch (Throwable t) {
            mod.log(AaReady.LINE + "준비 시각 훅 없음, 모두 다시 보냄 (" + t.getClass().getSimpleName() + ")");
        }
        try {
            final Class<?> c = Class.forName(AaReady.PP_LISTENER, false, app.classLoader);
            Hooks.findAndHookMethod(c, "runningPP", new MethodHook() {
                @Override
                protected void afterHookedMethod(Param p) {
                    final Object listener = p.thisObject;
                    final long since = android.os.SystemClock.elapsedRealtime();
                    // B-1(1.7.4): 신호가 아직 0이면 AaReady가 1초마다 최대 60초 다시 본다(그 뒤 반드시 끝남)
                    AaReady.onRunningPP(new AaReady.Signal() {
                        @Override
                        public boolean on() {
                            return AaWifi.on(mod);
                        }

                        @Override
                        public boolean modOff() {
                            return !mod.enabled();
                        }
                    }, new AaReady.Scheduler() {
                        @Override
                        public void postDelayed(Runnable r, long ms) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(r, ms);
                        }
                    }, new Runnable() {
                        @Override
                        public void run() {
                            try {
                                AaReady.resend(app.classLoader, listener, log, since, android.os.SystemClock.elapsedRealtime());
                            } catch (Throwable t) {
                                mod.log(AaReady.LINE + "못 함 (" + t.getClass().getSimpleName() + ")");
                            }
                        }
                    }, log);
                }
            });
            mod.log("Android Auto: ready resend hook in " + app.processName + " (active only while " + AaWifi.PROP + "=1)");
        } catch (Throwable t) {
            mod.log(AaReady.LINE + "훅 없음: GPM 구조가 다름 (" + t.getClass().getSimpleName() + ")");
        }
    }

    private void usbDrives(final ModContext mod, LoadedApp app) {
        mod.hook(null, "framework.context_impl.start_service", new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                Intent i = (Intent) p.args[0];
                ComponentName c = i == null ? null : i.getComponent();
                if (c == null || !GAL_SERVICE.equals(c.getClassName()) || i.getIntExtra("startMode", -1) != 1) return;
                UsbDevice d = i.getParcelableExtra("device");
                if (!notPhone(d)) return;
                p.setResult(null);
                mod.log("USB drive or hub (" + d.getDeviceName() + "), not starting wired Android Auto");
            }
        });
        mod.log("USB drive hook in " + app.processName);
    }

    /** true when the device has interfaces and every one is mass storage or a hub */
    static boolean notPhone(UsbDevice d) {
        if (d == null || d.getInterfaceCount() == 0) return false;
        for (int k = 0; k < d.getInterfaceCount(); k++) {
            int cls = d.getInterface(k).getInterfaceClass();
            if (cls != USB_CLASS_MASS_STORAGE && cls != USB_CLASS_HUB) return false;
        }
        return true;
    }

    /** the Korean build leaves btsettings.projection_helper.open_wireless_page out of mod.json */
    static final boolean MENU_DECLARED = false;

    private void phoneProjectionMenu(final ModContext mod, LoadedApp app) {
        final ClassLoader cl = app.classLoader;
        mod.hook(cl, HELPER + ".open_wireless_page", new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                int region = Car.region();
                if (region != Car.REGION_US && region != Car.REGION_CANADA) return;   // other regions: stock choice
                Context c = (Context) p.args[0];
                if (Boolean.TRUE.equals(Points.call(cl, HELPER + ".device_connections_available", null))) {
                    Points.call(cl, HELPER + ".open_device_connections", null, c, 1);
                } else {
                    Points.call(cl, HELPER + ".open_other_page", null, c);
                }
                p.setResult(null);
                mod.log("Phone Projection: Device Connections (US/Canada), not the wireless page");
            }
        });
        mod.log("Phone Projection menu hook in " + app.processName);
    }
}
