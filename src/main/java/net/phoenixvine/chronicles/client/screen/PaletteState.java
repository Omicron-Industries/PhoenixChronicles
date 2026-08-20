package net.phoenixvine.chronicles.client.screen;

import net.phoenixvine.wiki.theme.PhoenixTheme;

class PaletteState {

    int bg = 0xFF0B0B0F;
    int panelDark = 0xFF0E0E12;
    int header = 0xFF09090D;
    int border = 0xFF252530;
    int borderLit = 0xFF353548;
    int selTab = 0xFF1A1A26;
    int selAccent = 0xFF00AA55;
    int nodeLocked = 0xFF1A1A24;
    int nodeUnlocked = 0xFF1E1E2C;
    int nodeActive = 0xFF221C00;
    int nodeDone = 0xFF081A0E;
    int nbordLocked = 0xFF2E2E40;
    int nbordUnlocked = 0xFF4A4A60;
    int nbordActive = 0xFFCC9900;
    int nbordDone = 0xFF00BB66;
    int nbordDev = 0xFF8844AA;
    int lineLocked = 0x38FFFFFF;
    int lineDone = 0x9900CC66;
    int lineActive = 0x88FFAA00;
    int text = 0xFFD8D8E4;
    int textDim = 0xFF7A7A8A;
    int textFaint = 0xFF404050;
    int textDone = 0xFF44CC88;
    int textAct = 0xFFFFBB33;
    int progFill = 0xFF00AA55;

    void refresh(PhoenixTheme t) {
        bg = t.bg.getColor();
        panelDark = t.header.getColor();
        header = t.header.getColor();
        border = t.border.getColor();
        borderLit = t.accent.getColor();
        selTab = t.panel.getColor();
        selAccent = t.accent.getColor();
        text = t.text.getColor();
        textDim = t.textDim.getColor();
        textFaint = t.textFaint.getColor();
        textDone = t.done.getColor();
        textAct = t.activeColor.getColor();
        progFill = t.accent.getColor();

        int bgc = t.bg.getColor();
        nodeLocked = ChronicleOverviewScreen.blendColor(bgc, t.locked.getColor(), 0.18f);
        nodeUnlocked = ChronicleOverviewScreen.blendColor(bgc, t.border.getColor(), 0.35f);

        nodeActive = ChronicleOverviewScreen.blendColor(bgc, t.activeColor.getColor(), 0.32f);
        nodeDone = ChronicleOverviewScreen.blendColor(bgc, t.done.getColor(), 0.30f);

        nbordLocked = ChronicleOverviewScreen.blendColor(t.locked.getColor(), 0xFF000000, 0.25f);
        nbordUnlocked = ChronicleOverviewScreen.blendColor(t.border.getColor(), 0xFFFFFFFF, 0.15f);
        if (luma(nbordLocked) >= luma(nbordUnlocked)) {

            nbordLocked = ChronicleOverviewScreen.blendColor(nbordUnlocked, 0xFF000000, 0.4f);
        }
        nbordActive = t.activeColor.getColor();
        nbordDone = t.done.getColor();
        nbordDev = ChronicleOverviewScreen.blendColor(t.accent.getColor(), 0xFFCC44FF, 0.5f);

        lineLocked = 0x38000000 | (t.locked.getColor() & 0x00FFFFFF);
        lineDone = 0x99000000 | (t.done.getColor() & 0x00FFFFFF);
        lineActive = 0x88000000 | (t.activeColor.getColor() & 0x00FFFFFF);
    }

    private static int luma(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return r * 299 + g * 587 + b * 114;
    }
}
