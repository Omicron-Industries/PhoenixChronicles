package net.phoenixvine.chronicles.capability.importer;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public final class LenientSnbtParser {

    private LenientSnbtParser() {}

    public static CompoundTag parse(String input) {
        Cursor c = new Cursor(input);
        c.skipIgnorable();
        Tag root = c.parseValue();
        c.skipIgnorable();
        if (!(root instanceof CompoundTag ct)) {
            throw new IllegalArgumentException("Root SNBT value is not a compound tag");
        }
        return ct;
    }

    private static final class Cursor {

        private final String s;
        private final int len;
        private int pos;

        Cursor(String s) {
            this.s = s;
            this.len = s.length();
        }

        private char peek() {
            return pos < len ? s.charAt(pos) : '\0';
        }

        private void skipIgnorable() {
            while (pos < len) {
                char c = s.charAt(pos);
                if (Character.isWhitespace(c) || c == ',') {
                    pos++;
                    continue;
                }
                if (c == '/' && pos + 1 < len && s.charAt(pos + 1) == '/') {
                    while (pos < len && s.charAt(pos) != '\n') pos++;
                    continue;
                }
                if (c == '#') {
                    while (pos < len && s.charAt(pos) != '\n') pos++;
                    continue;
                }
                break;
            }
        }

        private void expect(char expected) {
            if (pos >= len || s.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + pos + " but found '" +
                        (pos < len ? s.charAt(pos) : "<eof>") + "'");
            }
            pos++;
        }

        Tag parseValue() {
            skipIgnorable();
            char c = peek();
            if (c == '{') return parseCompound();
            if (c == '[') return parseList();
            if (c == '"' || c == '\'') return StringTag.valueOf(parseQuotedString(c));
            return parseBareToken();
        }

        private CompoundTag parseCompound() {
            expect('{');
            CompoundTag tag = new CompoundTag();
            skipIgnorable();
            while (peek() != '}') {
                if (pos >= len) throw new IllegalArgumentException("Unterminated compound tag");
                String key = parseKey();
                skipIgnorable();
                expect(':');
                Tag value = parseValue();
                tag.put(key, value);
                skipIgnorable();
            }
            pos++;
            return tag;
        }

        private String parseKey() {
            skipIgnorable();
            char c = peek();
            if (c == '"' || c == '\'') return parseQuotedString(c);
            int start = pos;
            while (pos < len) {
                char ch = s.charAt(pos);
                if (ch == ':' || Character.isWhitespace(ch)) break;
                pos++;
            }
            if (pos == start) throw new IllegalArgumentException("Expected a key at position " + pos);
            return s.substring(start, pos);
        }

        private Tag parseList() {
            expect('[');
            skipIgnorable();

            char maybeType = peek();
            if (maybeType == 'I' || maybeType == 'B' || maybeType == 'L') {
                int save = pos;
                pos++;
                skipIgnorable();
                if (peek() == ';') {
                    pos++;
                    return parseTypedArray(maybeType);
                }
                pos = save;
            }
            ListTag list = new ListTag();
            skipIgnorable();
            while (peek() != ']') {
                if (pos >= len) throw new IllegalArgumentException("Unterminated list tag");
                list.add(parseValue());
                skipIgnorable();
            }
            pos++;
            return list;
        }

        private Tag parseTypedArray(char type) {
            java.util.List<Long> values = new java.util.ArrayList<>();
            skipIgnorable();
            while (peek() != ']') {
                if (pos >= len) throw new IllegalArgumentException("Unterminated array tag");
                Tag v = parseValue();
                if (v instanceof net.minecraft.nbt.NumericTag n) values.add(n.getAsLong());
                skipIgnorable();
            }
            pos++;
            return switch (type) {
                case 'B' -> {
                    byte[] arr = new byte[values.size()];
                    for (int i = 0; i < arr.length; i++) arr[i] = values.get(i).byteValue();
                    yield new net.minecraft.nbt.ByteArrayTag(arr);
                }
                case 'L' -> {
                    long[] arr = new long[values.size()];
                    for (int i = 0; i < arr.length; i++) arr[i] = values.get(i);
                    yield new net.minecraft.nbt.LongArrayTag(arr);
                }
                default -> {
                    int[] arr = new int[values.size()];
                    for (int i = 0; i < arr.length; i++) arr[i] = values.get(i).intValue();
                    yield new net.minecraft.nbt.IntArrayTag(arr);
                }
            };
        }

        private String parseQuotedString(char quote) {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < len) {
                char c = s.charAt(pos);
                if (c == '\\' && pos + 1 < len) {
                    char next = s.charAt(pos + 1);
                    sb.append(switch (next) {
                        case 'n' -> '\n';
                        case 't' -> '\t';
                        case 'r' -> '\r';
                        default -> next;
                    });
                    pos += 2;
                    continue;
                }
                if (c == quote) {
                    pos++;
                    return sb.toString();
                }
                sb.append(c);
                pos++;
            }
            throw new IllegalArgumentException("Unterminated string starting before position " + pos);
        }

        private Tag parseBareToken() {
            int start = pos;
            while (pos < len) {
                char c = s.charAt(pos);
                if (c == '{' || c == '}' || c == '[' || c == ']' || c == ':' || c == ',' || Character.isWhitespace(c))
                    break;
                pos++;
            }
            String tok = s.substring(start, pos);
            if (tok.isEmpty()) {
                throw new IllegalArgumentException("Expected a value at position " + pos);
            }
            return tokenToTag(tok);
        }

        private Tag tokenToTag(String tok) {
            if (tok.equalsIgnoreCase("true")) return ByteTag.valueOf(true);
            if (tok.equalsIgnoreCase("false")) return ByteTag.valueOf(false);

            char last = tok.charAt(tok.length() - 1);
            try {
                switch (Character.toLowerCase(last)) {
                    case 'b' -> {
                        return ByteTag.valueOf(Byte.parseByte(tok.substring(0, tok.length() - 1)));
                    }
                    case 's' -> {
                        return ShortTag.valueOf(Short.parseShort(tok.substring(0, tok.length() - 1)));
                    }
                    case 'l' -> {
                        return LongTag.valueOf(Long.parseLong(tok.substring(0, tok.length() - 1)));
                    }
                    case 'f' -> {
                        return FloatTag.valueOf(Float.parseFloat(tok.substring(0, tok.length() - 1)));
                    }
                    case 'd' -> {
                        return DoubleTag.valueOf(Double.parseDouble(tok.substring(0, tok.length() - 1)));
                    }
                    default -> {
                        if (tok.indexOf('.') >= 0) return DoubleTag.valueOf(Double.parseDouble(tok));
                        return IntTag.valueOf(Integer.parseInt(tok));
                    }
                }
            } catch (NumberFormatException e) {

                return StringTag.valueOf(tok);
            }
        }
    }
}
