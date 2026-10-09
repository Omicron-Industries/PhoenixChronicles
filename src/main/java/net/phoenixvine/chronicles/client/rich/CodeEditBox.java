package net.phoenixvine.chronicles.client.rich;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class CodeEditBox extends EditBox {

    public CodeEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    @Override
    public void insertText(String text) {
        super.insertText(text.length() > 1 ? CodeInput.convertPasted(text) : text);
        convertTypedCodes();
    }

    private void convertTypedCodes() {
        String v = getValue();
        int c = getCursorPosition();
        if (c >= 2 && v.charAt(c - 2) == '&' && CodeInput.isCodeChar(v.charAt(c - 1))) {
            setValue(v.substring(0, c - 2) + '§' + v.substring(c - 1));
            moveCursorTo(c);
            return;
        }
        if (c >= 8 && v.charAt(c - 8) == '&' && v.charAt(c - 7) == '#' && CodeInput.isHex6(v, c - 6)) {
            setValue(v.substring(0, c - 8) + "{#" + v.substring(c - 6, c) + "}" + v.substring(c));
            moveCursorTo(c + 1);
        }
    }
}
