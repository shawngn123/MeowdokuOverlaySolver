package com.shawngn123.meowdokuoverlaysolver;

public enum DebugStage {
    RAW("01_Raw.png"),
    BOARD_BOUNDS("02_BoardBounds.png"),
    GRID_LINES("03_GridLines.png"),
    CELL_CENTERS("04_CellCenters.png"),
    COLOR_CLUSTERS("05_ColorClusters.png"),
    REGIONS("06_Regions.png"),
    SOLUTION("07_Solution.png"),
    TOUCH_TARGETS("08_TouchTargets.png");

    public final String fileName;

    DebugStage(String fileName) {
        this.fileName = fileName;
    }
}
