import hmgapi.engine.HookPlan;
import hmgapi.engine.Json;
import java.nio.file.*;
import java.util.*;

/** Hook points missing on NQ5.KOR 260604 (#46): does HMG-API HookPlan still load this mod's hooks? */
public class HookPlanCheck {
    @SuppressWarnings("unchecked")
    public static void main(String[] a) throws Exception {
        String[] missingPts = { "btsettings.projection_helper.device_connections_available", "btsettings.projection_helper.open_device_connections",
            "btsettings.projection_helper.open_other_page", "btsettings.projection_helper.open_wireless_page", "themesettings.split_item.type" };
        for (int k = 0; k < a.length; k += 2) {
            Map<String, Object> mod = Json.object(new String(Files.readAllBytes(Paths.get(a[k + 1])), "UTF-8"));
            Map<String, Object> pts = (Map<String, Object>) ((Map<String, Object>) mod.get("hooks")).get("points");
            Set<String> known = new HashSet<String>(pts.keySet());
            for (String m : missingPts) known.add(m);
            Map<String, String> missing = new HashMap<String, String>();
            for (String m : missingPts) { known.remove(m); missing.put(m, "0 methods match"); }
            Map<String, Map<String, Object>> mods = new HashMap<String, Map<String, Object>>();
            mods.put("wireless-carplay-aa", mod);
            HookPlan p = HookPlan.of(mods, known, missing);
            java.lang.reflect.Field f = HookPlan.class.getDeclaredField("refused"); f.setAccessible(true);
            Map<String, String> refused = (Map<String, String>) f.get(p);
            System.out.println(a[k] + ": " + (refused.isEmpty() ? "hooks load" : "REFUSED " + refused));
        }
    }
}
