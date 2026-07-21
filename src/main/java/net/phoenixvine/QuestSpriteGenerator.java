package net.phoenixvine;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class QuestSpriteGenerator {

    private static final int SPRITE_SIZE = 64;
    
    private static final String OUTPUT_DIR = "src/main/resources/assets/phoenix_chronicles/textures/gui/sprites/";

    public static void main(String[] args) {
        File dir = new File(OUTPUT_DIR);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                System.err.println("Critical Error: Failed to create output directory structural path!");
                return;
            }
        }

        try {
            System.out.println("=================================================");
            System.out.println("Starting crisp 16x16 Pixel Sprite Asset Baking...");
            System.out.println("Target: " + dir.getAbsolutePath());
            System.out.println("=================================================");

            generateShape("square.png", false, new int[] { 1, 14, 14, 1 }, new int[] { 1, 1, 14, 14 });
            generateShape("square_outline.png", true, new int[] { 1, 14, 14, 1 }, new int[] { 1, 1, 14, 14 });

            generateShape("circle.png", false, new int[] { 4, 11, 14, 14, 11, 4, 1, 1 },
                    new int[] { 1, 1, 4, 11, 14, 14, 11, 4 });
            generateShape("circle_outline.png", true, new int[] { 4, 11, 14, 14, 11, 4, 1, 1 },
                    new int[] { 1, 1, 4, 11, 14, 14, 11, 4 });

            generateShape("diamond.png", false, new int[] { 8, 14, 8, 1 }, new int[] { 1, 8, 14, 8 });
            generateShape("diamond_outline.png", true, new int[] { 8, 14, 8, 1 }, new int[] { 1, 8, 14, 8 });

            generateShape("hexagon.png", false, new int[] { 4, 11, 14, 11, 4, 1 }, new int[] { 1, 1, 8, 14, 14, 8 });
            generateShape("hexagon_outline.png", true, new int[] { 4, 11, 14, 11, 4, 1 },
                    new int[] { 1, 1, 8, 14, 14, 8 });

            generateShape("triangle.png", false, new int[] { 8, 14, 1 }, new int[] { 1, 14, 14 });
            generateShape("triangle_outline.png", true, new int[] { 8, 14, 1 }, new int[] { 1, 14, 14 });

            generateShape("star.png", false, new int[] { 8, 10, 15, 11, 13, 8, 3, 5, 1, 6 },
                    new int[] { 1, 6, 6, 9, 14, 11, 14, 9, 6, 6 });
            generateShape("star_outline.png", true, new int[] { 8, 10, 15, 11, 13, 8, 3, 5, 1, 6 },
                    new int[] { 1, 6, 6, 9, 14, 11, 14, 9, 6, 6 });

            generateShape("pentagon.png", false, new int[] { 8, 14, 12, 4, 2 }, new int[] { 1, 5, 14, 14, 5 });
            generateShape("pentagon_outline.png", true, new int[] { 8, 14, 12, 4, 2 }, new int[] { 1, 5, 14, 14, 5 });

            generateShape("shield.png", false, new int[] { 1, 14, 14, 8, 1 }, new int[] { 1, 1, 9, 14, 9 });
            generateShape("shield_outline.png", true, new int[] { 1, 14, 14, 8, 1 }, new int[] { 1, 1, 9, 14, 9 });

            generateCross("cross.png", false);
            generateCross("cross_outline.png", true);

            generateArrowhead("line_arrow.png");
            generateGridPattern("bg_dot_grid.png");

            generateLinePattern("dep_line_dotted.png", new boolean[] { true, false });               
                                                                                                     
            generateLinePattern("dep_line_dashed.png", new boolean[] { true, true, true, false, false }); 
                                                                                                          
            generateLineBadge("line_badge_forbidden.png", true);                                  
                                                                                                  
            generateLineBadge("line_badge_cosmetic.png", false);                                 
                                                                                                 
            generateLineCurve("line_curve_in.png", true);                                        
                                                                                                 
            generateLineCurve("line_curve_out.png", false);                                      

            System.out.println("=================================================");
            System.out.println("SUCCESS: All 20 UI assets baked completely!");
            System.out.println("=================================================");
        } catch (IOException e) {
            System.err.println("CRITICAL FAILABLE CRASH during texture encoding stream loop!");
            e.printStackTrace();
        }
    }

    private static void generateShape(String filename, boolean outlineOnly, int[] x, int[] y) throws IOException {
        BufferedImage img = new BufferedImage(SPRITE_SIZE, SPRITE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        setupGraphics(g);

        if (outlineOnly) {
            g.drawPolygon(x, y, x.length);
        } else {
            g.fillPolygon(x, y, x.length);
        }

        g.dispose();
        ImageIO.write(img, "png", new File(OUTPUT_DIR + filename));
    }

    private static void generateCross(String filename, boolean outlineOnly) throws IOException {
        BufferedImage img = new BufferedImage(SPRITE_SIZE, SPRITE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        setupGraphics(g);

        int[] x = { 5, 10, 10, 14, 14, 10, 10, 5, 5, 1, 1, 5 };
        int[] y = { 1, 1, 5, 5, 10, 10, 14, 14, 10, 10, 5, 5 };

        if (outlineOnly) {
            g.drawPolygon(x, y, 12);
        } else {
            g.fillPolygon(x, y, 12);
        }

        g.dispose();
        ImageIO.write(img, "png", new File(OUTPUT_DIR + filename));
    }

    private static void generateArrowhead(String filename) throws IOException {
        BufferedImage img = new BufferedImage(SPRITE_SIZE, SPRITE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        setupGraphics(g);

        int[] x = { 8, 13, 10, 8, 6, 3 };
        int[] y = { 2, 12, 10, 11, 10, 12 };

        g.fillPolygon(x, y, 6);
        g.dispose();
        ImageIO.write(img, "png", new File(OUTPUT_DIR + filename));
    }

    private static void generateGridPattern(String filename) throws IOException {
        BufferedImage img = new BufferedImage(SPRITE_SIZE, SPRITE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setColor(new Color(255, 255, 255, 45));
        g.fillRect(0, 0, 1, 1);

        g.dispose();
        ImageIO.write(img, "png", new File(OUTPUT_DIR + filename));
    }

    private static void generateLinePattern(String filename, boolean[] pattern) throws IOException {
        
        BufferedImage img = new BufferedImage(pattern.length, 1, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < pattern.length; x++) {
            img.setRGB(x, 0, pattern[x] ? Color.WHITE.getRGB() : 0x00000000);
        }
        ImageIO.write(img, "png", new File(OUTPUT_DIR + filename));
    }

    private static void generateLineBadge(String filename, boolean isForbidden) throws IOException {
        BufferedImage img = new BufferedImage(SPRITE_SIZE, SPRITE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        setupGraphics(g);

        if (isForbidden) {
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(3, 3, 12, 12);
            g.drawLine(12, 3, 3, 12);
        } else {
            g.drawOval(2, 4, 12, 8);
            g.fillOval(6, 6, 4, 4);
        }
        g.dispose();
        ImageIO.write(img, "png", new File(OUTPUT_DIR + filename));
    }

    private static void generateLineCurve(String filename, boolean curveIn) throws IOException {
        BufferedImage img = new BufferedImage(SPRITE_SIZE, SPRITE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        setupGraphics(g);
        g.setStroke(new BasicStroke(1.0f));

        if (curveIn) {
            g.drawArc(-16, 0, 31, 31, 270, 90);
        } else {
            g.drawArc(0, -16, 31, 31, 180, 90);
        }
        g.dispose();
        ImageIO.write(img, "png", new File(OUTPUT_DIR + filename));
    }

    private static void setupGraphics(Graphics2D g) {
        
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.setColor(Color.WHITE);
    }
}

