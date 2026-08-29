package net.phoenixvine.chronicles;

import net.phoenixvine.chronicles.flag.FlagExpression;
import net.phoenixvine.chronicles.model.CategoryDefinition;
import net.phoenixvine.chronicles.model.GroupIcon;
import net.phoenixvine.chronicles.model.IconKind;
import net.phoenixvine.chronicles.model.QuestGroup;
import net.phoenixvine.chronicles.model.QuestGroupManager;
import net.phoenixvine.chronicles.model.QuestState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Real proof of every hotc port so far, exercised at once, including QuestGroupManager's own
// mutable-static-backed persistence (Gson JSON round-trip through a real temp directory).
public final class HotcProbe {
    public static void main(String[] args) throws Exception {
        HotcInfo info = HotcInfo.current();
        System.out.println(info.describe());

        List<String> chapters = new ArrayList<>();
        chapters.add("intro");
        chapters.add("main_quest");
        CategoryDefinition base = new CategoryDefinition("main", "Main Quests", chapters, 0, "", 0);
        System.out.println("CategoryDefinition: id=" + base.id() + " chapters=" + base.chapters().size());

        System.out.println("QuestState: LOCKED == LOCKED -> " + (QuestState.LOCKED == QuestState.LOCKED));
        System.out.println("QuestState: valueOf(ACTIVE) == ACTIVE -> " + (QuestState.valueOf("ACTIVE") == QuestState.ACTIVE));

        QuestGroup grp = QuestGroup.new3("grp1", "Group One", "ALL");
        grp.addIcon(IconKind.ITEM, "minecraft:diamond");
        grp.addIcon(IconKind.FLUID, "minecraft:water");
        System.out.println("QuestGroup: id=" + grp.getId() + " icons=" + grp.getIcons().size());

        // QuestGroupManager: real mutable-static-backed state + real Gson JSON round-trip.
        QuestGroupManager.clear();
        QuestGroupManager.put(grp);
        QuestGroupManager.put(QuestGroup.new3("grp2", "Group Two", "chapter1"));
        System.out.println("getAll().size()=" + QuestGroupManager.getAll().size());
        System.out.println("formatColor(0x22FFFFFF)=" + QuestGroupManager.formatColor(0x22FFFFFF));
        System.out.println("parseColor(#22FFFFFF)=" + Integer.toHexString(QuestGroupManager.parseColor("#22FFFFFF")));
        System.out.println("generateId()=" + QuestGroupManager.generateId());

        Path tmp = Files.createTempDirectory("hcprobe");
        QuestGroupManager.save(tmp.toString());
        System.out.println("saved groups.json, exists=" + Files.exists(tmp.resolve("groups.json")));
        System.out.println("--- groups.json ---");
        System.out.println(Files.readString(tmp.resolve("groups.json")));

        QuestGroupManager.invalidate();
        QuestGroupManager.clear();
        System.out.println("after clear, getAll().size()=" + QuestGroupManager.getAll().size());
        QuestGroupManager.load(tmp.toString());
        System.out.println("after load, getAll().size()=" + QuestGroupManager.getAll().size());
        for (Object o : QuestGroupManager.getAll()) {
            QuestGroup g = (QuestGroup) o;
            System.out.println("  loaded group: id=" + g.getId() + " icons=" + g.getIcons().size() + " x=" + g.getX());
            for (Object io : (List<Object>) g.getIcons()) {
                GroupIcon gi = (GroupIcon) io;
                System.out.println("    icon: " + gi.kind.name() + " " + gi.id);
            }
        }

        QuestGroupManager.remove("grp1");
        System.out.println("after remove(grp1), getAll().size()=" + QuestGroupManager.getAll().size());

        // FlagExpression: real EXISTS/EQ/NEQ/GT/GTE/LT/LTE coverage, including the two real
        // null-workaround paths (null `actual` -> NPE-caught -> 0, and a non-numeric `actual` on a
        // numeric op -> NumberFormatException-caught -> 0) described in FlagExpression.hotc's own
        // header.
        FlagExpression exExpr = FlagExpression.parse("my_flag");
        System.out.println("FlagExpression EXISTS: key=" + exExpr.key
                + " test(\"1\")=" + (exExpr.test("1") == 1)
                + " test(\"false\")=" + (exExpr.test("false") == 1)
                + " test(\"\")=" + (exExpr.test("") == 1)
                + " test(null)=" + (exExpr.test(null) == 1));

        FlagExpression eqExpr = FlagExpression.parse("mode=Ready");
        System.out.println("FlagExpression EQ: key=" + eqExpr.key
                + " test(\"ready\")=" + (eqExpr.test("ready") == 1)
                + " test(\"nope\")=" + (eqExpr.test("nope") == 1));

        FlagExpression neqExpr = FlagExpression.parse("mode!=Ready");
        System.out.println("FlagExpression NEQ: test(\"ready\")=" + (neqExpr.test("ready") == 1)
                + " test(\"nope\")=" + (neqExpr.test("nope") == 1));

        FlagExpression gtExpr = FlagExpression.parse("level>5");
        System.out.println("FlagExpression GT: test(\"10\")=" + (gtExpr.test("10") == 1)
                + " test(\"3\")=" + (gtExpr.test("3") == 1)
                + " test(\"not_a_number\")=" + (gtExpr.test("not_a_number") == 1));

        FlagExpression gteExpr = FlagExpression.parse("level>=5");
        System.out.println("FlagExpression GTE: test(\"5\")=" + (gteExpr.test("5") == 1));

        FlagExpression ltExpr = FlagExpression.parse("level<5");
        System.out.println("FlagExpression LT: test(\"3\")=" + (ltExpr.test("3") == 1));

        FlagExpression lteExpr = FlagExpression.parse("level<=5");
        System.out.println("FlagExpression LTE: test(\"5\")=" + (lteExpr.test("5") == 1)
                + " test(null)=" + (lteExpr.test(null) == 1));
    }
}
