package PlatyMatch.nucleiDetection;

import net.imglib2.Point;

public class RichFeaturePoint {

    private int x;
    private int y;
    private int z;
    private int scale;
    private float value;
    private int red;
    private int green;
    private int blue;
    private int redOld;
    private int greenOld;
    private int blueOld;
    private boolean isSelected;
    private String gene;
    private double geneExpression;
    private int label;


    public RichFeaturePoint(int x, int y, int z, int scale, float value, int red, int green, int blue) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.scale = scale;
        this.value = value;
        this.red = red;
        this.redOld = red;
        this.green = green;
        this.greenOld = green;
        this.blue = blue;
        this.blueOld = blue;
        this.isSelected = false;
    }


    public int getX() {
        return this.x;
    }


    public int getY() {
        return this.y;

    }

    public int getZ() {

        return this.z;
    }

    public int getScale() {
        return this.scale;
    }


    public float getValue() {
        return this.value;
    }


    public int getRed() {
        return this.red;

    }

    public int getGreen() {
        return this.green;
    }


    public int getBlue() {
        return this.blue;
    }


    public Point getLocation() {
        int[] location = new int[3];
        location[0] = getX();
        location[1] = getY();
        location[2] = getZ();
        return new Point(location);
    }

    public int getLocation(int dim) {
        int temp = 0;
        if (dim == 0) {
            temp = getX();
        } else if (dim == 1) {
            temp = getY();
        } else {
            temp = getZ();
        }

        return temp;
    }

    public int compareTo(RichFeaturePoint p) {
        return (int) (this.value - p.getValue());
        //return (int) (-this.scale + p.getScale());

    }

    public boolean getSelected() {
        return this.isSelected;
    }

    public void setSelected(boolean b) {
        this.isSelected = b;
    }

    public void setBlue(int i) {
        this.blue = i;
    }

    public void setGreen(int i) {
        this.green = i;

    }

    public void setRed(int i) {
        this.red = i;
    }

    public int getRedOld() {
        return this.redOld;
    }

    public int getGreenOld() {
        return this.greenOld;
    }

    public int getBlueOld() {
        return this.blueOld;
    }

    public void setGeneExpression(String text, double val) {
        this.gene = text;
        this.geneExpression = val;

    }

    public void setLabel(int name){
        this.label=name;
    }

    public int getLabel() {
        return this.label;
    }
}
