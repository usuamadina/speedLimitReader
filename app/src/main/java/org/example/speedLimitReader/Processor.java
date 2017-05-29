package org.example.speedLimitReader;

import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
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
    Point centerCircle;
    Mat tabla_caracteristicas;
    Mat binaria1;
    Mat binaria2;
    Mat entrada_gris;
    int NUMERO_CLASES = 10;
    int MUESTRAS_POR_CLASE = 2;
    int NUMERO_CARACTERISTICAS = 9;

    public Processor() { //Constructor
        red = new Mat();
        green = new Mat();
        blue = new Mat();
        maxGB = new Mat();
        tabla_caracteristicas = new Mat(NUMERO_CLASES * MUESTRAS_POR_CLASE,
                NUMERO_CARACTERISTICAS, CvType.CV_64FC1);
        binaria1 = new Mat();
        binaria2 = new Mat();
        entrada_gris = new Mat();
        crearTabla();
    }

    public Mat process(Mat input) {
        Mat zr = RedZones(input);
        Mat binary = binarize(zr);
        Rect r = findRedCircle(binary);
        zr.release();
        binary.release();
        if (r.height == -1 && r.width == -1)
            return input.clone();

        ArrayList<Integer> digitsInsideSign = innerDiscSegmentation(input, r);
        Log.d("digitsInsideSign", " = " + digitsInsideSign);
        if (digitsInsideSign.size() < 2 || digitsInsideSign.size() > 3)
            return input.clone();
        int finalDigits = orderDigits(digitsInsideSign);
        if (finalDigits ==0)
            return input.clone();
        Mat output = dibujarResultado(input, r, finalDigits);
        return output;
    }


    public int orderDigits(ArrayList<Integer> digits) {
        String numbers = "";
        Log.d("orderingDigits","Antes de ordenar los digitos");
        switch (digits.size()) {
            case 2:
                if (digits.get(0) == 0) {
                    numbers = numbers + digits.get(1).toString() + digits.get(0).toString();
                } else
                    numbers = numbers + digits.get(0).toString() + digits.get(1).toString();


                break;
            case 3:
                String first = new String();
                String last = new String();
                int i = 0;
                boolean found = false;
                while (i < digits.size() && !found) {
                    if (digits.get(i) == 1) {
                        first = digits.get(i).toString();
                        digits.remove(i);
                        found = true;
                    } else {
                        i++;
                    }
                }
                found = false;
                i = 0;
                while (i < digits.size() && !found) {
                    if (digits.get(i) == 0) {
                        last = digits.get(i).toString();
                        digits.remove(i);
                        found = true;
                    }
                    i++;
                }
                numbers = first + digits.get(0).toString() + last;
                break;

        }
        Log.d("Limite velocidad", " = " + numbers);
       // if (numbers=="")
       //     return 0;
        return Integer.parseInt(numbers);
    }

    Mat dibujarResultado(Mat imagen, Rect digit_rect, int digit) {
        Mat salida = imagen.clone();
        Point P1 = new Point(digit_rect.x, digit_rect.y);
        Point P2 = new Point(digit_rect.x + digit_rect.width, digit_rect.y + digit_rect.height);
        Imgproc.rectangle(salida, P1, P2, new Scalar(255, 0, 0));
        // Escribir numero
        int fontFace = 6;//FONT_HERSHEY_SCRIPT_SIMPLEX;
        double fontScale = 1;
        int thickness = 5;
        Imgproc.putText(salida, Integer.toString(digit), P1, fontFace, fontScale,
                new Scalar(0, 0, 0), thickness, 8, false);
        Imgproc.putText(salida, Integer.toString(digit),
                P1, fontFace, fontScale,
                new Scalar(255, 255, 255), thickness / 2, 8, false);
        return salida;
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
        for (int c = 0; c < blobs.size(); c++) {
            // Log.d("Processor", "Numero de columnas de hierarchy = " + hierarchy.cols());
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
            centerCircle = getCenter(blobs.get(c));
            if (isCircle(blobs.get(c), centerCircle)) {
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

    public ArrayList<Integer> innerDiscSegmentation(Mat input, Rect rect) {
        Mat red = input.clone();
        ArrayList<Integer> croppedDigits = new ArrayList<Integer>();
        Core.extractChannel(red, red, 0);
        Mat binary = otsuBinarization(red);
        Log.d("Segm.Interior", "antes de Otsu");
        Mat rectangle = binary.submat(rect);
        Log.d("Segm.Interior", "hace bien la binarización otsu");
        ArrayList<Rect> segmentationDigits = digitSegmentation(rectangle, rect);
        if (segmentationDigits.size() == 0)
            return croppedDigits;
        for (int i = 0; i < segmentationDigits.size(); i++) {
            //Recortamos rectángulo en imagen original
            Log.d("innerDiscSegmentation", "recortamos digitos");
            Mat cropped = binary.submat(segmentationDigits.get(i));
            Log.d("innerDiscSegmentation", "leemos digitos");
            croppedDigits.add(leerRectangulo(cropped));
            Log.d("innerDiscSegmentation", "Leido digito" + leerRectangulo(cropped));
        }

        binary.release();
        red.release();
        rectangle.release();
        return croppedDigits;
    }

    public Mat otsuBinarization(Mat input) {
        Log.d("otsu", "hasta aquí llega bien");
        Log.d("otsu", "tipo entrada " + input.type());
        Imgproc.threshold(input, input, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);
        return input;
    }

    public ArrayList<Rect> digitSegmentation(Mat input, Rect rect) {
        //  Log.d("Segmentación Digitos", "segmentando");
        List<MatOfPoint> blobs = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(input, blobs, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_NONE);
        int minimumHeight = 12;
        ArrayList<Rect> digits = new ArrayList();
        // Seleccionar candidatos a circulos
        for (int c = 0; c < blobs.size(); c++) {
            //Log.d("Processor", "Numero de columnas de hierarchy = " + hierarchy.cols());
            double[] data = hierarchy.get(0, c);
            int parent = (int) data[3];
            // Nos quedamos con el contorno externo, padre = -1
            if (parent > 0)
                continue;
            Rect BB = Imgproc.boundingRect(blobs.get(c));
            // Comprobar altura > 1/3 del cuadradito
            if (BB.height < rect.height / 3)
                continue;
            // Comprobar altura > 12pixels
            if (BB.height < minimumHeight)
                continue;
            if (BB.height < BB.width)
                continue;
            // Comprobar no está cerca del borde
            if (BB.x < 2 || BB.y < 2)
                continue;
            // Comprar que el numero está centrado en la señal
            Point numberCenter = getCenter(blobs.get(c));
            if ((numberCenter.y - centerCircle.y) < 0.5) {
                BB.x = BB.x + rect.x;
                BB.y = BB.y + rect.y;
                //final Point P1 = new Point(BB.x, BB.y);
                //final Point P2 = new Point(BB.x + BB.width, BB.y + BB.height);
                digits.add(BB);
            }
        }

        hierarchy.release();
        red.release();
        return digits;

    }

    void crearTabla() {
        double datosEntrenamiento[][] = new double[][]{
                new double[]{0.5757916569709778, 0.8068438172340393, 0.6094995737075806, 0.6842694878578186, 0, 0.6750765442848206, 0.573646605014801, 0.814811110496521, 0.6094995737075806},
                new double[]{0.5408163070678711, 0.04897959157824516, 0, 0.8428571224212646, 0.79795902967453, 0.7795917987823486, 0.9938775897026062, 1, 0.995918333530426},
                new double[]{0.7524304986000061, 0.1732638627290726, 0.697916567325592, 0.6704860925674438, 0.3805555701255798, 0.9767361283302307, 0.6843749284744263, 0.7732638716697693, 0.6086806654930115},
                new double[]{0.6724254488945007, 0, 0.6819106936454773, 0.6561655402183533, 0.5406503081321716, 0.647357702255249, 0.6775066256523132, 0.8231707215309143, 0.732723593711853},
                new double[]{0.02636498026549816, 0.6402361392974854, 0.5215936899185181, 0.7385144829750061, 0.5210034847259521, 0.6062962412834167, 0.5685194730758667, 0.6251844167709351, 0.7910475134849548},
                new double[]{0.8133208155632019, 0.550218939781189, 0.6083046793937683, 0.7753458619117737, 0.4955636858940125, 0.6764461994171143, 0.4960368871688843, 0.8128473162651062, 0.6384715437889099},
                new double[]{0.6108391284942627, 0.985664427280426, 0.5884615778923035, 0.7125874161720276, 0.5996503829956055, 0.6629370450973511, 0.4828671216964722, 0.7608392238616943, 0.6695803999900818},
                new double[]{0.6381308436393738, 0, 0.1727102696895599, 0.7140188217163086, 0.5850467085838318, 0.8407476544380188, 0.943925142288208, 0.4654205441474915, 0.02728971838951111},
                new double[]{0.6880735158920288, 0.8049609065055847, 0.7363235950469971, 0.6299694776535034, 0.672782838344574, 0.6411824822425842, 0.6687054634094238, 0.7784574031829834, 0.7037037014961243},
                new double[]{0.6497123241424561, 0.7168009877204895, 0.4542001485824585, 0.6476410031318665, 0.6150747537612915, 0.7033372521400452, 0.5941311717033386, 0.9686998724937439, 0.5930955410003662},
                new double[]{0.6764705777168274, 1, 0.7450980544090271, 0.7091502547264099, 0.05228758603334427, 0.6993464231491089, 0.6339869499206543, 0.9934640526771545, 0.7058823704719543},
                new double[]{0.3452012538909912, 0.3885449171066284, 0, 0.7770897746086121, 0.6501547694206238, 0.5789474248886108, 1, 1, 1},
                new double[]{0.6407563090324402, 0.06722689419984818, 0.7825630307197571, 0.7132352590560913, 0.6365545988082886, 0.9222689270973206, 0.7226890921592712, 0.5850840210914612, 0.7058823704719543},
                new double[]{0.5980392098426819, 0, 0.6666666865348816, 0.686274528503418, 0.5751633644104004, 0.6111111640930176, 0.6111112236976624, 0.7516340017318726, 0.7647058963775635},
                new double[]{0.03549695760011673, 0.717038631439209, 0.4705882370471954, 0.7474644780158997, 0.7109533548355103, 0.6531440615653992, 0.5862069725990295, 0.6744422316551208, 0.780933141708374},
                new double[]{0.6201297640800476, 0.5129870772361755, 0.5876624584197998, 0.7207792997360229, 0.5844155550003052, 0.6168831586837769, 0.5389610528945923, 0.8214285969734192, 0.7435064911842346},
                new double[]{0.6176470518112183, 1, 0.6764706373214722, 0.6699347496032715, 0.601307213306427, 0.6405228972434998, 0.5098039507865906, 0.7647058963775635, 0.8039215803146362},
                new double[]{0.7272727489471436, 0.0202020201832056, 0.2727272808551788, 0.8383838534355164, 0.8181818127632141, 0.7272727489471436, 0.8989898562431335, 0.1616161614656448, 0},
                new double[]{0.6928104758262634, 0.8071895837783813, 0.8333333134651184, 0.6764705777168274, 0.7026143074035645, 0.6209149956703186, 0.6601307392120361, 0.7712417840957642, 0.7941176891326904},
                new double[]{0.7320261597633362, 0.8202614784240723, 0.5653595328330994, 0.6503268480300903, 0.5882353186607361, 0.6732026338577271, 0.6045752167701721, 0.9869281649589539, 0.6339869499206543}};
        for (int i = 0; i < 20; i++)
            tabla_caracteristicas.put(i, 0, datosEntrenamiento[i]);
    }

    public int leerRectangulo(Mat rectangulo) {
        Mat vectorCaracteristicas = caracteristicas(rectangulo);
        // Buscamos la fila de la tabla que mas se parece
        double Sumvv = vectorCaracteristicas.dot(vectorCaracteristicas);
        int nmin = 0;
        double Sumvd = tabla_caracteristicas.row(nmin).dot(vectorCaracteristicas);
        double Sumdd = tabla_caracteristicas.row(nmin).dot(tabla_caracteristicas.row(nmin));
        double D = Sumvd / Math.sqrt(Sumvv * Sumdd);
        double dmin = D;
        for (int n = 1; n < tabla_caracteristicas.rows(); n++) {
            Sumvd = tabla_caracteristicas.row(n).dot(vectorCaracteristicas);
            Sumdd = tabla_caracteristicas.row(n).dot(tabla_caracteristicas.row(n));
            D = Sumvd / Math.sqrt(Sumvv * Sumdd);
            if (D > dmin) {
                dmin = D;
                nmin = n;
            }
        }
        // A partir de la fila determinamos el numero
        nmin = nmin % 10;
        return nmin;
    }

    public Mat caracteristicas(Mat recorteDigito) { //rectangulo: imagen binaria de digito
        //Convertimos a flotante doble precisión
        Mat chardouble = new Mat();
        recorteDigito.convertTo(chardouble, CvType.CV_64FC1);
        //Calculamos vector de caracteristicas
        Mat digito_3x3 = new Mat();
        Imgproc.resize(chardouble, digito_3x3, new Size(3, 3), 0, 0, Imgproc.INTER_AREA);
        // convertimos de 3x3 a 1x9 en el orden adecuado
        digito_3x3 = digito_3x3.t();

        chardouble.release();
       return digito_3x3.reshape(1, 1);
    }


   /* public Mat linearContrastIncrease(Mat input) {
        MatOfInt channels;
        MatOfInt binsNumber;
        MatOfFloat interval;
        Mat hist;
        List<Mat> images;
        float[] histogram;

        channels = new MatOfInt(0);
        binsNumber = new MatOfInt(256);
        interval = new MatOfFloat(0, 256);
        hist = new Mat();
        images = new ArrayList<Mat>();
        histogram = new float[256];

        Mat output = new Mat();
        images.clear(); //Eliminar imagen anterior si la hay
        images.add(input); //Añadir imagen actual
        Imgproc.calcHist(images, channels, new Mat(), hist,
                binsNumber, interval);
        //Lectura del histogram a un array de float
        hist.get(0, 0, histogram);
        //Calcular xmin y xmax

        int total_pixeles = input.cols() * input.rows();
        float saturationPercentage = (float) 0.05;
        int saturatedPixels = (int) (saturationPercentage * total_pixeles);
        int xmin = 0;
        int xmax = 255;
        float accumulated = 0f;
        for (int n = 0; n < 256; n++) { //xmin
            accumulated = accumulated + histogram[n];
            if (accumulated > saturatedPixels) {
                xmin = n;
                break;
            }
        }
        accumulated = 0;
        for (int n = 255; n >= 0; n--) { //xmax
            accumulated = accumulated + histogram[n];
            if (accumulated > saturatedPixels) {
                xmax = n;
                break;
            }
        }

        //Calculo de la salida
        Core.subtract(input, new Scalar(xmin), output);
        float slope = ((float) 255.0) / ((float) (xmax - xmin));
        Core.multiply(output, new Scalar(slope), output);
        channels.release();
        binsNumber.release();
        interval.release();
        hist.release();
        return output;

    }*/
}











