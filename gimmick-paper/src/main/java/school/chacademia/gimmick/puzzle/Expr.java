package school.chacademia.gimmick.puzzle;

import java.util.*;

/**
 * Puzzle logic expressions (no Bukkit). Grammar:
 * <pre>
 *   expr  := name | fn '(' args ')'
 *   fn    := all | any | not | seq | count | sync
 *   all(a,b,..)      every input on
 *   any(a,b,..)      at least one on
 *   not(x)
 *   seq(a,b,c)       all on, and they turned on in this order
 *   count(n,a,b,..)  at least n inputs on
 *   sync(sec,a,b,..) all on, and all turned on within `sec` seconds of each other
 * </pre>
 */
public sealed interface Expr {
    interface Ctx { boolean on(String node); long since(String node); }

    boolean eval(Ctx c);
    /** Every node name referenced. */
    void names(Set<String> out);

    record Ref(String name) implements Expr {
        public boolean eval(Ctx c) { return c.on(name); }
        public void names(Set<String> out) { out.add(name); }
    }
    record All(List<Expr> args) implements Expr {
        public boolean eval(Ctx c) { for (Expr e : args) if (!e.eval(c)) return false; return true; }
        public void names(Set<String> out) { args.forEach(a -> a.names(out)); }
    }
    record Any(List<Expr> args) implements Expr {
        public boolean eval(Ctx c) { for (Expr e : args) if (e.eval(c)) return true; return false; }
        public void names(Set<String> out) { args.forEach(a -> a.names(out)); }
    }
    record Not(Expr arg) implements Expr {
        public boolean eval(Ctx c) { return !arg.eval(c); }
        public void names(Set<String> out) { arg.names(out); }
    }
    record Count(int n, List<Expr> args) implements Expr {
        public boolean eval(Ctx c) { int k = 0; for (Expr e : args) if (e.eval(c)) k++; return k >= n; }
        public void names(Set<String> out) { args.forEach(a -> a.names(out)); }
    }
    record Seq(List<String> order) implements Expr {
        public boolean eval(Ctx c) {
            long prev = Long.MIN_VALUE;
            for (String n : order) {
                if (!c.on(n)) return false;
                long s = c.since(n);
                if (s < prev) return false;
                prev = s;
            }
            return true;
        }
        public void names(Set<String> out) { out.addAll(order); }
        /** True while the inputs that are on form a correct prefix of the order. */
        public boolean prefixOk(Ctx c) {
            var onNames = new ArrayList<String>();
            for (String n : order) if (c.on(n)) onNames.add(n);
            onNames.sort(Comparator.comparingLong(c::since));
            for (int i = 0; i < onNames.size(); i++) if (!onNames.get(i).equals(order.get(i))) return false;
            return true;
        }
    }
    record Sync(double seconds, List<String> nodes) implements Expr {
        public boolean eval(Ctx c) {
            long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
            for (String n : nodes) {
                if (!c.on(n)) return false;
                min = Math.min(min, c.since(n)); max = Math.max(max, c.since(n));
            }
            return max - min <= Math.round(seconds * 20);
        }
        public void names(Set<String> out) { out.addAll(nodes); }
    }

    /** All Seq expressions inside this tree. */
    static List<Seq> seqs(Expr e) {
        var out = new ArrayList<Seq>();
        collect(e, out);
        return out;
    }
    private static void collect(Expr e, List<Seq> out) {
        switch (e) {
            case Seq s -> out.add(s);
            case All a -> a.args().forEach(x -> collect(x, out));
            case Any a -> a.args().forEach(x -> collect(x, out));
            case Not n -> collect(n.arg(), out);
            case Count k -> k.args().forEach(x -> collect(x, out));
            default -> {}
        }
    }

    // ------------------------------------------------------------------ parser
    static Expr parse(String text) {
        var p = new Parser(text);
        Expr e = p.expr();
        p.skip();
        if (p.i < p.s.length()) throw new IllegalArgumentException("식 끝에 남은 글자: " + text.substring(p.i));
        return e;
    }

    final class Parser {
        final String s; int i = 0;
        Parser(String s) { this.s = s == null ? "" : s.trim(); }
        void skip() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        String word() {
            skip();
            int start = i;
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '-' || s.charAt(i) == '.')) i++;
            if (start == i) throw new IllegalArgumentException("이름이 필요한 위치: " + (i < s.length() ? s.substring(i) : "(끝)"));
            return s.substring(start, i);
        }
        boolean peek(char ch) { skip(); return i < s.length() && s.charAt(i) == ch; }
        void expect(char ch) {
            if (!peek(ch)) throw new IllegalArgumentException("'" + ch + "' 가 필요함: " + s);
            i++;
        }
        Expr expr() {
            String w = word();
            if (!peek('(')) return new Ref(w);
            expect('(');
            var args = new ArrayList<String>();
            var exprs = new ArrayList<Expr>();
            String fn = w.toLowerCase(Locale.ROOT);
            boolean numericFirst = fn.equals("count") || fn.equals("sync");
            boolean namesOnly = fn.equals("seq") || fn.equals("sync");
            int index = 0;
            if (!peek(')')) {
                do {
                    if (index > 0) expect(',');
                    if (numericFirst && index == 0) args.add(word());
                    else if (namesOnly) args.add(word());
                    else exprs.add(expr());
                    index++;
                } while (peek(','));
            }
            expect(')');
            return switch (fn) {
                case "all", "and" -> new All(List.copyOf(exprs));
                case "any", "or" -> new Any(List.copyOf(exprs));
                case "not" -> { if (exprs.size() != 1) throw new IllegalArgumentException("not() 은 하나만"); yield new Not(exprs.getFirst()); }
                case "seq" -> new Seq(List.copyOf(args));
                case "count" -> new Count(Integer.parseInt(args.getFirst()), List.copyOf(exprs));
                case "sync" -> new Sync(Double.parseDouble(args.getFirst()), List.copyOf(args.subList(1, args.size())));
                default -> throw new IllegalArgumentException("모르는 함수: " + w);
            };
        }
    }
}
