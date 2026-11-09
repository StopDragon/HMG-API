import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * AA 라이브러리 레시피(aa-tbt-*)의 UTF-8 자르기 함수(세그먼트 0x17a, 32바이트) 시험(1.7.2 M-15).
 *
 * 레시피 세그먼트의 실제 바이트를 작은 Thumb 해석기로 돌린다. 함수는 (r0 = 문자열 주소, r1 = 길이)를 받아
 * r0에 보낼 길이를 돌려준다. 그 길이로 std::string을 만들고 순정 콜백이 NewStringUTF로 Java에 넘긴다.
 * 원래 함수(아래 OLD)는 끝의 이어지는 바이트만 지워 "우회전"이 EC로 끝났다("우회쀀"). 고친 함수는
 * 96바이트 이하면 그대로, 넘으면 글자 경계까지 줄인다.
 */
public final class AaTrimTest {
    static int fail;
    static final int FN = 0x17a, FN_LEN = 32, LIMIT = 96;
    /** 업스트림 원래 바이트(고치기 전) */
    static final String OLD = "6029024628bf602108460139535c03f0c003802b00d070470029f5d108467047";
    /** 고친 바이트 */
    static final String NEW = "02460846602807d96020135c03f0c003802b01d10138f8d1704700bf00bf00bf";
    static final Charset UTF8 = Charset.forName("UTF-8");

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) fail = 1;
    }

    static byte[] hex(String h) {
        byte[] b = new byte[h.length() / 2];
        for (int k = 0; k < b.length; k++) b[k] = (byte) Integer.parseInt(h.substring(2 * k, 2 * k + 2), 16);
        return b;
    }

    static String toHex(byte[] b, int off, int len) {
        StringBuilder s = new StringBuilder();
        for (int k = 0; k < len; k++) s.append(String.format("%02x", b[off + k] & 0xff));
        return s.toString();
    }

    /** 이 함수가 쓰는 Thumb 명령만 아는 해석기. 모르는 명령이면 예외 */
    static final class Cpu {
        final byte[] code;
        final byte[] mem = new byte[0x2000];
        final int[] r = new int[16];
        /** r4~r15 표시 값(함수가 건드리지 않았는지 보려고) */
        static final int MARK = 0x7e570000;
        boolean n, z, c, v;
        int readLo = Integer.MAX_VALUE, readHi = -1;

        Cpu(byte[] code) {
            this.code = code;
        }

        int half(int pc) {
            return (code[pc] & 0xff) | (code[pc + 1] & 0xff) << 8;
        }

        boolean cond(int cc) {
            switch (cc) {
                case 0: return z;
                case 1: return !z;
                case 2: return c;
                case 3: return !c;
                case 4: return n;
                case 5: return !n;
                case 8: return c && !z;
                case 9: return !c || z;
                case 10: return n == v;
                case 11: return n != v;
                case 12: return !z && n == v;
                case 13: return z || n != v;
                default: throw new IllegalStateException("cond " + cc);
            }
        }

        void sub(int a, int b) {
            int res = a - b;
            n = res < 0;
            z = res == 0;
            c = Integer.compareUnsigned(a, b) >= 0;
            v = ((a ^ b) & (a ^ res)) < 0;
        }

        /** 문자열을 0x1000에 두고 함수를 돌려 r0을 돌려준다 */
        int run(byte[] s, int len) {
            int base = 0x1000;
            System.arraycopy(s, 0, mem, base, s.length);
            r[0] = base;
            r[1] = len;
            r[2] = 0x5a5a;
            r[3] = 0x5a5a;
            for (int k = 4; k < 16; k++) r[k] = MARK + k;
            int pc = 0, steps = 0, itCond = -1;
            while (true) {
                if (++steps > 10000) throw new IllegalStateException("no return");
                if (pc < 0 || pc + 2 > code.length) throw new IllegalStateException("pc " + pc);
                int op = half(pc);
                boolean exec = true;
                if (itCond >= 0) {
                    exec = cond(itCond);
                    itCond = -2;   // 한 명령짜리 IT만
                }
                int next = pc + 2;
                if (op == 0x4770) {
                    if (exec) return r[0];
                } else if (op == 0xbf00) {
                    // nop
                } else if ((op & 0xff00) == 0xbf00) {
                    if ((op & 0xf) != 8) throw new IllegalStateException("IT mask");
                    itCond = (op >> 4) & 0xf;
                    pc = next;
                    continue;
                } else if ((op & 0xff00) == 0x4600) {
                    int rd = ((op >> 4) & 8) | (op & 7), rm = (op >> 3) & 0xf;
                    if (exec) r[rd] = r[rm];
                } else if ((op & 0xf800) == 0x2800) {
                    if (exec) sub(r[(op >> 8) & 7], op & 0xff);
                } else if ((op & 0xf800) == 0x2000) {
                    if (exec) {
                        int rd = (op >> 8) & 7;
                        r[rd] = op & 0xff;
                        if (itCond == -1) { n = false; z = r[rd] == 0; }
                    }
                } else if ((op & 0xf800) == 0x3800) {
                    if (exec) {
                        int rd = (op >> 8) & 7, a = r[rd];
                        r[rd] = a - (op & 0xff);
                        if (itCond == -1) sub(a, op & 0xff);
                    }
                } else if ((op & 0xfe00) == 0x5c00) {
                    if (exec) {
                        int addr = r[(op >> 3) & 7] + r[(op >> 6) & 7];
                        readLo = Math.min(readLo, addr - base);
                        readHi = Math.max(readHi, addr - base);
                        r[op & 7] = mem[addr] & 0xff;
                    }
                } else if ((op & 0xfbf0) == 0xf000) {
                    int op2 = half(pc + 2);
                    if ((op & 0x0400) != 0 || (op2 & 0xf000) != 0) throw new IllegalStateException("and imm form");
                    if (exec) r[(op2 >> 8) & 0xf] = r[op & 0xf] & (op2 & 0xff);
                    next = pc + 4;
                } else if ((op & 0xf000) == 0xd000 && (op & 0x0f00) < 0x0e00) {
                    if (exec && cond((op >> 8) & 0xf)) next = pc + 4 + ((byte) (op & 0xff)) * 2;
                } else {
                    throw new IllegalStateException(String.format("op %04x at %x", op, pc));
                }
                if (itCond == -2) itCond = -1;
                pc = next;
            }
        }
    }

    /** 기대값: min(len, 96) 안에 드는 가장 긴 온전한 글자들의 바이트 수 */
    static int expected(String s) {
        int total = s.getBytes(UTF8).length;
        if (total <= LIMIT) return total;
        int n = 0;
        for (int k = 0; k < s.length(); ) {
            int cp = s.codePointAt(k);
            int w = cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
            if (n + w > LIMIT) break;
            n += w;
            k += Character.charCount(cp);
        }
        return n;
    }

    static String repeat(String s, int k) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < k; i++) b.append(s);
        return b.toString();
    }

    static int run(byte[] fn, String s) {
        byte[] b = s.getBytes(UTF8);
        return new Cpu(fn).run(b, b.length);
    }

    /** 고친 함수: 결과가 기대값이고, 앞부분이 온전한 UTF-8이며, 문자열 밖을 읽지 않는다 */
    static void caseNew(byte[] fn, String what, String s) {
        byte[] b = s.getBytes(UTF8);
        Cpu cpu = new Cpu(fn);
        int got = cpu.run(b, b.length);
        int want = expected(s);
        String head = new String(b, 0, Math.max(0, got), UTF8);
        boolean clean = got >= 0 && got <= b.length && s.startsWith(head) && head.getBytes(UTF8).length == got;
        boolean inside = cpu.readHi < 0 || (cpu.readLo >= 0 && cpu.readHi < b.length);
        // 호출부가 기대는 레지스터: r1(길이)과 r4 이상은 그대로
        boolean kept = cpu.r[1] == b.length;
        for (int k = 4; k < 16; k++) if (cpu.r[k] != Cpu.MARK + k) kept = false;
        check("M-15 trim: " + what + " (len " + b.length + " -> " + got + ", want " + want + ")",
                got == want && clean && inside && kept);
    }

    public static void main(String[] a) throws Exception {
        byte[] fn = null;
        for (String path : a) {
            String text = new String(Files.readAllBytes(Paths.get(path)), UTF8);
            String seg = null;
            for (String line : text.split("\n")) if (line.startsWith("segment ")) seg = line.split("\\s+")[2];
            byte[] b = hex(seg);
            String got = toHex(b, FN, FN_LEN);
            check("M-15: " + Paths.get(path).getFileName() + " has the fixed trim function at 0x17a", NEW.equals(got));
            check("M-15: " + Paths.get(path).getFileName() + " has the old bytes nowhere else", !text.contains(OLD));
            byte[] f = new byte[FN_LEN];
            System.arraycopy(b, FN, f, 0, FN_LEN);
            fn = f;
        }

        // 원인 재현: 원래 함수는 "우회전"(9바이트)에서 7을 돌려줘 EC(전의 첫 바이트)만 남긴다
        byte[] old = hex(OLD);
        byte[] w = "우회전".getBytes(UTF8);
        int o = run(old, "우회전");
        int lead = w[o - 1] & 0xff;
        check("M-15 cause: old function cuts \"우회전\" to 7 bytes ending in EC", o == 7 && lead == 0xec);
        check("M-15 cause: EC with zero continuation bits is U+C000 (쀀), as on the #67 cluster",
                (char) ((lead & 0x0f) << 12) == '쀀');
        check("M-15 cause: old function also breaks short ASCII-free names (\"강남대로\" 12 -> 10)", run(old, "강남대로") == 10);
        check("M-15 cause: old function leaves ASCII alone", run(old, "Main St") == 7);

        byte[] f = hex(NEW);
        caseNew(f, "Hangul short \"우회전\" unchanged", "우회전");
        caseNew(f, "ASCII short unchanged", "Main St");
        caseNew(f, "one Hangul", "전");
        caseNew(f, "empty: 0, nothing read", "");
        caseNew(f, "Hangul exactly 96 bytes (32 chars)", repeat("전", 32));
        caseNew(f, "Hangul 99 bytes: cut at 96 (a boundary)", repeat("전", 33));
        caseNew(f, "a + Hangul 97 bytes: cut back to 94", "a" + repeat("전", 32));
        caseNew(f, "ab + Hangul 98 bytes: cut back to 95", "ab" + repeat("전", 32));
        caseNew(f, "ASCII 120 bytes: 96", repeat("x", 120));
        String emoji = new String(Character.toChars(0x1F600));
        caseNew(f, "emoji 4-byte under the limit", "a" + emoji + "b");
        caseNew(f, "a + emoji x24 (97 bytes): cut back to 93", "a" + repeat(emoji, 24));
        caseNew(f, "2-byte chars across the edge", "a" + repeat("é", 60));
        java.util.Random rnd = new java.util.Random(15);
        String[] parts = { "a", "é", "전", emoji, " " };
        boolean all = true;
        for (int k = 0; k < 2000; k++) {
            StringBuilder b = new StringBuilder();
            int m = rnd.nextInt(60);
            for (int i = 0; i < m; i++) b.append(parts[rnd.nextInt(parts.length)]);
            String s = b.toString();
            byte[] by = s.getBytes(UTF8);
            Cpu cpu = new Cpu(f);
            int got = cpu.run(by, by.length);
            if (got != expected(s) || (cpu.readHi >= 0 && cpu.readHi >= by.length)) all = false;
        }
        check("M-15 trim: 2000 random mixed strings: always the longest whole-char prefix within 96, reads inside", all);
        check("M-15 trim: the recipe's bytes are the ones tested", fn != null && toHex(fn, 0, FN_LEN).equals(NEW));
        System.exit(fail);
    }
}
