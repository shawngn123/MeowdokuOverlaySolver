package com.shawngn123.meowdokuoverlaysolver.analyzer;

import com.shawngn123.meowdokuoverlaysolver.AnalysisResult;
import com.shawngn123.meowdokuoverlaysolver.ArgbImage;
import com.shawngn123.meowdokuoverlaysolver.BoardGeometry;
import com.shawngn123.meowdokuoverlaysolver.DebugStage;
import com.shawngn123.meowdokuoverlaysolver.FloatRect;
import com.shawngn123.meowdokuoverlaysolver.PuzzleModel;
import com.shawngn123.meowdokuoverlaysolver.PuzzlePipeline;
import com.shawngn123.meowdokuoverlaysolver.PuzzleSolver;
import com.shawngn123.meowdokuoverlaysolver.RegionMap;
import com.shawngn123.meowdokuoverlaysolver.TouchTarget;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

import javax.imageio.ImageIO;

public final class OfflineAnalyzer {
    private OfflineAnalyzer() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: gradle :analyzer:run --args \"<screenshot.png> [output-dir]\"");
            System.exit(2);
        }

        File input = new File(args[0]);
        if (!input.isFile()) {
            System.err.println("Screenshot not found: " + input.getAbsolutePath());
            System.exit(2);
        }
        File output = args.length == 2
                ? new File(args[1])
                : new File("build/meowdoku-analyzer/" + stripExtension(input.getName()));
        Files.createDirectories(output.toPath());

        BufferedImage image = ImageIO.read(input);
        if (image == null) {
            System.err.println("Unsupported image file: " + input.getAbsolutePath());
            System.exit(2);
        }
        ArgbImage argb = toArgbImage(image);
        AnalysisResult result = new PuzzlePipeline().analyze(argb);
        for (DebugStage stage : DebugStage.values()) {
            BufferedImage rendered = render(image, result, stage);
            ImageIO.write(rendered, "png", new File(output, stage.fileName));
        }

        System.out.println(result.summary());
        if (result.regions != null) {
            System.out.println("Region grid:");
            System.out.println(result.regions.compactRows());
        }
        System.out.println("Debug images: " + output.getAbsolutePath());
        if (!result.isSuccess()) {
            System.exit(1);
        }
    }

    private static ArgbImage toArgbImage(BufferedImage source) {
        BufferedImage argbImage = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = argbImage.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        int[] pixels = argbImage.getRGB(0, 0, argbImage.getWidth(), argbImage.getHeight(), null, 0, argbImage.getWidth());
        return ArgbImage.wrapCopy(argbImage.getWidth(), argbImage.getHeight(), pixels);
    }

    private static BufferedImage render(BufferedImage source, AnalysisResult result, DebugStage stage) {
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (stage != DebugStage.RAW) drawStage(graphics, output, result, stage);
        graphics.dispose();
        return output;
    }

    private static void drawStage(Graphics2D graphics, BufferedImage output, AnalysisResult result, DebugStage stage) {
        BoardGeometry board = result.board;
        if (board == null) {
            drawMessage(graphics, output, result.failureReason == null ? "Board not detected" : result.failureReason);
            return;
        }

        switch (stage) {
            case BOARD_BOUNDS:
                drawBounds(graphics, board);
                break;
            case GRID_LINES:
                drawGrid(graphics, board);
                break;
            case CELL_CENTERS:
                drawGrid(graphics, board);
                drawCenters(graphics, board);
                break;
            case COLOR_CLUSTERS:
                if (result.regions != null) drawSampledColors(graphics, board, result.regions);
                drawGrid(graphics, board);
                break;
            case REGIONS:
                if (result.regions != null) drawRegions(graphics, board, result.regions);
                drawGrid(graphics, board);
                break;
            case SOLUTION:
                if (result.regions != null) drawRegions(graphics, board, result.regions);
                drawSolution(graphics, board, result.model, result.solution);
                drawGrid(graphics, board);
                break;
            case TOUCH_TARGETS:
                if (result.regions != null) drawRegions(graphics, board, result.regions);
                drawTouchTargets(graphics, board, result);
                drawGrid(graphics, board);
                break;
            case RAW:
                break;
        }
        if (result.failureReason != null) drawMessage(graphics, output, result.failureReason);
    }

    private static void drawBounds(Graphics2D graphics, BoardGeometry board) {
        graphics.setStroke(new BasicStroke(Math.max(4f, board.averageCellSize() * 0.055f)));
        graphics.setColor(new Color(0, 210, 90, 245));
        drawRect(graphics, board.bounds);
    }

    private static void drawGrid(Graphics2D graphics, BoardGeometry board) {
        drawBounds(graphics, board);
        graphics.setStroke(new BasicStroke(Math.max(1.5f, board.averageCellSize() * 0.022f)));
        graphics.setColor(new Color(0, 180, 255, 235));
        for (float x : board.xLines) {
            graphics.draw(new Line2D.Float(x, board.bounds.top, x, board.bounds.bottom));
        }
        for (float y : board.yLines) {
            graphics.draw(new Line2D.Float(board.bounds.left, y, board.bounds.right, y));
        }
    }

    private static void drawCenters(Graphics2D graphics, BoardGeometry board) {
        graphics.setColor(new Color(255, 50, 50, 245));
        float radius = Math.max(3f, board.averageCellSize() * 0.055f);
        for (int row = 0; row < board.rows; row++) {
            for (int column = 0; column < board.columns; column++) {
                fillCircle(graphics, board.centerX(column), board.centerY(row), radius);
            }
        }
    }

    private static void drawSampledColors(Graphics2D graphics, BoardGeometry board, RegionMap regions) {
        for (int row = 0; row < regions.size; row++) {
            for (int column = 0; column < regions.size; column++) {
                int color = regions.sampledColor(row, column);
                graphics.setColor(new Color(
                        (color >> 16) & 0xff,
                        (color >> 8) & 0xff,
                        color & 0xff,
                        218
                ));
                drawFillRect(graphics, board.cellRect(row, column));
            }
        }
    }

    private static void drawRegions(Graphics2D graphics, BoardGeometry board, RegionMap regions) {
        for (int row = 0; row < regions.size; row++) {
            for (int column = 0; column < regions.size; column++) {
                graphics.setColor(debugRegionColor(regions.regionId(row, column), 130));
                drawFillRect(graphics, board.cellRect(row, column));
            }
        }

        graphics.setColor(new Color(20, 20, 20, 245));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(Math.max(14f, board.averageCellSize() * 0.23f))));
        for (int row = 0; row < regions.size; row++) {
            for (int column = 0; column < regions.size; column++) {
                String label = Integer.toString(regions.regionId(row, column));
                int width = graphics.getFontMetrics().stringWidth(label);
                float x = board.centerX(column) - width * 0.5f;
                float y = board.centerY(row) - (graphics.getFontMetrics().getAscent() + graphics.getFontMetrics().getDescent()) * -0.25f;
                graphics.drawString(label, x, y);
            }
        }
    }

    private static void drawSolution(Graphics2D graphics, BoardGeometry board, PuzzleModel model, PuzzleSolver.Result solution) {
        if (solution == null || solution.columns == null) return;
        float radius = Math.max(8f, board.averageCellSize() * 0.24f);
        for (int row = 0; row < solution.columns.length; row++) {
            int column = solution.columns[row];
            if (column < 0 || column >= board.columns) continue;
            boolean existing = model != null && model.occupied[row][column];
            graphics.setColor(existing ? new Color(60, 60, 60, 230) : new Color(255, 45, 190, 230));
            fillCircle(graphics, board.centerX(column), board.centerY(row), radius);
        }
    }

    private static void drawTouchTargets(Graphics2D graphics, BoardGeometry board, AnalysisResult result) {
        graphics.setStroke(new BasicStroke(Math.max(4f, board.averageCellSize() * 0.06f)));
        graphics.setColor(new Color(255, 45, 190, 245));
        float radius = Math.max(10f, board.averageCellSize() * 0.28f);
        for (TouchTarget target : result.touchTargets) {
            graphics.draw(new Ellipse2D.Float(target.x - radius, target.y - radius, radius * 2f, radius * 2f));
            graphics.draw(new Line2D.Float(target.x - radius, target.y, target.x + radius, target.y));
            graphics.draw(new Line2D.Float(target.x, target.y - radius, target.x, target.y + radius));
        }
    }

    private static void drawMessage(Graphics2D graphics, BufferedImage output, String message) {
        if (message == null || message.isEmpty()) return;
        int textSize = Math.max(24, Math.round(output.getWidth() * 0.032f));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, textSize));
        String[] lines = message.split("\\n");
        int lineHeight = Math.round(textSize * 1.25f);
        int boxHeight = lineHeight * lines.length + 28;
        graphics.setColor(new Color(30, 30, 30, 220));
        graphics.fillRoundRect(16, 16, output.getWidth() - 32, boxHeight, 18, 18);
        graphics.setColor(Color.WHITE);
        int y = 34 + graphics.getFontMetrics().getAscent();
        for (String line : lines) {
            graphics.drawString(line, 30, y);
            y += lineHeight;
        }
    }

    private static Color debugRegionColor(int region, int alpha) {
        float hue = ((region * 137.508f) % 360f) / 360f;
        Color color = Color.getHSBColor(hue, 0.68f, 1f);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static void drawRect(Graphics2D graphics, FloatRect rect) {
        graphics.draw(new java.awt.geom.Rectangle2D.Float(rect.left, rect.top, rect.width(), rect.height()));
    }

    private static void drawFillRect(Graphics2D graphics, FloatRect rect) {
        graphics.fill(new java.awt.geom.Rectangle2D.Float(rect.left, rect.top, rect.width(), rect.height()));
    }

    private static void fillCircle(Graphics2D graphics, float x, float y, float radius) {
        graphics.fill(new Ellipse2D.Float(x - radius, y - radius, radius * 2f, radius * 2f));
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }
}
