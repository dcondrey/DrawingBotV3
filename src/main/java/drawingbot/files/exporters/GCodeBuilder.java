package drawingbot.files.exporters;

import drawingbot.DrawingBotV3;
import drawingbot.plotting.PlottingTask;
import drawingbot.utils.DBConstants;
import drawingbot.utils.Limit;
import drawingbot.utils.Utils;

import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.io.PrintWriter;
import java.util.function.Function;

public class GCodeBuilder {

    public final PlottingTask task;
    private final PrintWriter output;

    public static final String WILDCARD_LAYER_NAME = "%LAYER_NAME%";

    public boolean isPenDown;

    ///tallies
    public float distanceMoved;
    public float distanceDown;
    public float distanceUp;
    public int pointsDrawn;
    public int penLifts;
    public int penDrops;

    public float lastX = 0, lastY = 0;
    public float lastMoveX = 0, lastMoveY = 0;
    public Limit dx = new Limit(), dy = new Limit();

    public GCodeBuilder(PlottingTask task, PrintWriter output) {
        this.task = task;
        this.output = output;
    }

    /**
     * Note comments can still be called before this, if needed
     */
    public void open() {
        comment("GCode generated by: " + DBConstants.appName + " " + DBConstants.appVersion);
        comment("Time: " + Utils.getDateAndTime());
        command(DrawingBotV3.INSTANCE.gcodeStartCode.getValue());

        isPenDown = true; //forces the first pen up command
        movePenUp();
    }

    /**
     * Must be called to save the file
     */
    public void close() {
        movePenUp();
        linearMoveG1(0, 0);
        command(DrawingBotV3.INSTANCE.gcodeEndCode.getValue());

        comment("Distance Moved: " + Utils.gcodeFloat(distanceMoved) + " mm");
        comment("Distance Moved (Pen Up): " + Utils.gcodeFloat(distanceUp) + " mm");
        comment("Distance Moved (Pen Down): " + Utils.gcodeFloat(distanceDown) + " mm");
        comment("Points Plotted: " + pointsDrawn + " points");
        comment("Pen Lifted: " + penLifts + " times");
        comment("Pen Dropped: " + penDrops + " times");
        comment("Min X: " + Utils.gcodeFloat(dx.min) + " Max X: " + Utils.gcodeFloat(dx.max));
        comment("Min Y: " + Utils.gcodeFloat(dy.min) + " Max Y: " + Utils.gcodeFloat(dy.max));

        output.flush();
        output.close();
    }

    public void movePenUp() {
        if (isPenDown) {
            output.println(DrawingBotV3.INSTANCE.gcodePenUpCode.getValue());
            isPenDown = false;
            penLifts++;
        }
    }

    public void movePenDown() {
        if (!isPenDown) {
            output.println(DrawingBotV3.INSTANCE.gcodePenDownCode.getValue());
            isPenDown = true;
            penDrops++;
        }
    }

    public void startLayer(String layerName) {
        output.println(DrawingBotV3.INSTANCE.gcodeStartLayerCode.getValue().replace(WILDCARD_LAYER_NAME, layerName));
    }


    public void endLayer(String layerName) {
        output.println(DrawingBotV3.INSTANCE.gcodeEndLayerCode.getValue().replace(WILDCARD_LAYER_NAME, layerName));
    }

    public void move(float[] coords, int type) {
        switch (type) {
            case PathIterator.SEG_MOVETO:
                movePenUp();
                linearMoveG1(coords[0], coords[1]);
                movePenDown();
                break;
            case PathIterator.SEG_LINETO:
                linearMoveG1(coords[0], coords[1]);
                break;
            case PathIterator.SEG_QUADTO:
                quadCurveG5(coords[0], coords[1], coords[2], coords[3]);
                break;
            case PathIterator.SEG_CUBICTO:
                bezierCurveG5(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
                break;
            case PathIterator.SEG_CLOSE:
                linearMoveG1(lastMoveX, lastMoveY);
                movePenUp();
                break;
        }
    }

    public void linearMoveG1(float xValue, float yValue) {
        output.println((isPenDown ? "G1" : "G0") + " X" + Utils.gcodeFloat(xValue) + " Y" + Utils.gcodeFloat(yValue));
        logMove(xValue, yValue);
        lastMoveX = xValue;
        lastMoveY = yValue;
    }

    public void quadCurveG5(float controlPX, float controlPY, float endX, float endY){
        output.println("G5 P" + Utils.gcodeFloat(controlPX - lastX) + " Q" + Utils.gcodeFloat(controlPY - lastY) + " X" + Utils.gcodeFloat(endX) + " Y" + Utils.gcodeFloat(endY));
        logMove(controlPX, controlPY);
        logMove(endX, endY);
    }

    public void bezierCurveG5(float controlP1X, float controlP1Y, float controlP2X, float controlP2Y, float endX, float endY){
        output.println("G5 I" + Utils.gcodeFloat(controlP1X - lastX) + " J" + Utils.gcodeFloat(controlP1Y - lastY) + " P" + Utils.gcodeFloat(controlP2X - endX) + " Q" + Utils.gcodeFloat(controlP2Y - endY) + " X" + Utils.gcodeFloat(endX) + " Y" + Utils.gcodeFloat(endY));
        logMove(controlP1X, controlP1Y);
        logMove(controlP2X, controlP2Y);
        logMove(endX, endY);
    }

    public void logMove(float xValue, float yValue){
        dx.update_limit(xValue);
        dy.update_limit(yValue);

        double distance = Point2D.distance(lastX, lastY, xValue, yValue);

        distanceMoved += distance;
        distanceUp += !isPenDown ? distance : 0;
        distanceDown += isPenDown ? distance : 0;

        if (isPenDown) {
            pointsDrawn++;
        }

        lastX = xValue;
        lastY = yValue;
    }

    public void command(String command) {
        output.println(command);
    }

    public void comment(String comment) {
        output.println(DrawingBotV3.INSTANCE.gcodeCommentType.get().formatter.apply(comment));
    }

    public enum CommentType{
        BRACKETS("Brackets ()", s -> "(" + s + ")"),
        SEMI_COLON("Semi-Colons ;", s -> ";" + s),
        NONE("None", s -> "");

        public String displayName;
        public Function<String, String> formatter;

        CommentType(String displayName, Function<String, String> formatter){
            this.displayName = displayName;
            this.formatter = formatter;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

}
