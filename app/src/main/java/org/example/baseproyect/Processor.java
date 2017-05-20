package org.example.baseproyect;

import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by usuwi on 17/04/2017.
 */

public class Processor {

    Mat red;
    Mat green;
    Mat blue;
    Mat maxGB;

    public Processor() { //Constructor
        red = new Mat();
        green = new Mat();
        blue = new Mat();
        maxGB = new Mat();
    }

    public Mat process(Mat input) {
        Mat zr = RedZones(input);
        Mat binary = binarize(zr);
        Rect r = findRedCircle(binary);
        zr.release();
        binary.release();
        if (r.height > -1 && r.width > -1) {
            final Point P1 = new Point(r.x, r.y);
            final Point P2 = new Point(r.x + r.width - 1, r.y + r.height - 1);

            Imgproc.rectangle(input, P1, P2, new Scalar(255, 0, 0));
        }

        return input;
    }


    public Mat RedZones(Mat in) {
        Mat output = new Mat();
        Core.extractChannel(in, red, 0);
        Core.extractChannel(in, green, 1);
        Core.extractChannel(in, blue, 2);
        Core.max(green, blue, maxGB);
        Core.subtract(red, maxGB, output);
        green.release();
        blue.release();
        red.release();
        maxGB.release();
        return output;
    }

    public Mat binarize(Mat input) {

        Core.MinMaxLocResult minMax = Core.minMaxLoc(input);
        int maximum = (int) minMax.maxVal;
        int thresh = maximum / 4;
        Imgproc.threshold(input, input, thresh, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);
        return input;

    }

    public Rect findRedCircle(Mat input) {
        Mat dilation = new Mat();
        Mat residue = new Mat();
        Mat SE;
        double tam = 3;
        SE = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(tam, tam));
        Imgproc.dilate(input, dilation, SE);
        Core.subtract(dilation, input, residue);

        SE.release();
        dilation.release();
        Mat binary = new Mat();
        int contraste = 2;
        int tamano = 7;
        Imgproc.adaptiveThreshold(residue, binary, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, tamano, -contraste);
        residue.release();

        List<MatOfPoint> blobs = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();
        Rect finalRect = new Rect();
        finalRect.width = -1;
        finalRect.height = -1;
        Mat salida = binary.clone();//Copia porque finContours modifica entrada
        Imgproc.cvtColor(salida, salida, Imgproc.COLOR_GRAY2RGBA);
        Imgproc.findContours(binary, blobs, hierarchy, Imgproc.RETR_TREE,
                Imgproc.CHAIN_APPROX_NONE);
        int minimumHeight = 30;
        float maxratio = (float) 0.75;
        // Seleccionar candidatos a circulos
        for (int c = 0; c < blobs.size(); c++){
            Log.d("Processor", "Numero de columnas de hierarchy = " + hierarchy.cols());
            double[] data = hierarchy.get(0, c);
            int parent = (int) data[3];
            if (parent < 0) //Contorno exterior: rechazar
                continue;
            Rect BB = Imgproc.boundingRect(blobs.get(c));
            // Comprobar tamaño
            if (BB.width < minimumHeight || BB.height < minimumHeight)
                continue;
            // Comprobar anchura similar a altura
            float wf = BB.width;
            float hf = BB.height;
            float ratio = wf / hf;
            if (ratio < maxratio || ratio > 1.0 / maxratio)
                continue;
            // Comprobar no está cerca del borde
            if (BB.x < 2 || BB.y < 2)
                continue;
            if (input.width() - (BB.x + BB.width) < 3 || input.height() - (BB.y + BB.height) < 3)
                continue;

            // Hasta aquí cumple todos los criterios. Aplicamos test de circularidad
            Point center = getCenter(blobs.get(c));
            if (isCircle(blobs.get(c), center)) {
                int[] depth = getDepth(hierarchy);
                if (depth[c] == 3) {
                    // final Point P1 = new Point(BB.x, BB.y);
                    // final Point P2 = new Point(BB.x + BB.width, BB.y + BB.height);
                    //if (BB.width > bigCircle.width || BB.height > bigCircle.height) {
                    finalRect = BB;
                }
            }
        }
        binary.release();
        hierarchy.release();
        salida.release();
        return finalRect;
    }

    public int[] getDepth(Mat hierarchy) {
        int[] depth = new int[hierarchy.cols()];
        for (int i = 0; i < hierarchy.cols(); i++) {
            //Para calcular la profundidad solo nos interesa estudiar el contorno padre
            int parent = (int) hierarchy.get(0, i)[3];
            if (parent < 0) { // parent == -1 ==> no tiene padre
                depth[i] = 1;
            } else if (parent > 0) {
                depth[i] = depth[parent] + 1;

            }
        }
        return depth;
    }


    private Point getCenter(MatOfPoint curBlob) {
        Point Sum = new Point(0.0, 0.0);
        Size sizeA = curBlob.size();
        for (int x = 0; x < sizeA.height; x++)
            for (int y = 0; y < sizeA.width; y++) {
                double[] pp = curBlob.get(x, y);
                Sum.x += pp[0];
                Sum.y += pp[1];
            }
        double number_countour_points = sizeA.width * sizeA.height;
        Sum.x /= number_countour_points;
        Sum.y /= number_countour_points;
        return Sum;
    }


    private Boolean isCircle(MatOfPoint curBlob, Point center) {
        double minDistance = 0;
        double maxDistance = 0;
        final double xCenter = center.x;
        final double yCenter = center.y;
        Size sizeA = curBlob.size();
        for (int x = 0; x < sizeA.height; x++)
            for (int y = 0; y < sizeA.width; y++) {
                double[] point = curBlob.get(x, y);
                double dist = Math.hypot(xCenter - point[0], yCenter - point[1]);
                if (x == 0 && y == 0) {
                    minDistance = Math.hypot(xCenter - point[0], yCenter - point[1]);
                    maxDistance = Math.hypot(xCenter - point[0], yCenter - point[1]);
                }

                if (dist < minDistance) {
                    minDistance = dist;
                }
                if (dist > maxDistance) {
                    maxDistance = dist;
                }
            }

        double r = minDistance / maxDistance;
        return (r >= Math.sin(20));
    }


}







