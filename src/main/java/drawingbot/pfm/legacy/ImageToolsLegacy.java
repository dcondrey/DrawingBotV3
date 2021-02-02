package drawingbot.pfm.legacy;

import drawingbot.DrawingBotV3;
import drawingbot.FXApplication;
import drawingbot.plotting.PlottingTask;
import processing.core.PConstants;
import processing.core.PImage;

import static processing.core.PApplet.*;

/**Original ImageTools Class*/
class ImageToolsLegacy {

    public static DrawingBotV3 app = DrawingBotV3.INSTANCE;

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageThreshold(PlottingTask task) {
        task.comment("image_threshold");
        task.getPlottingImage().filter(PConstants.THRESHOLD); //THRESHOLD
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageDesaturate(PlottingTask task) {
        task.comment("image_desaturate");
        task.getPlottingImage().filter(PConstants.GRAY); //GRAY
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageInvert(PlottingTask task) {
        task.comment("image_invert");
        task.getPlottingImage().filter(PConstants.INVERT); //INVERT
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imagePosterize(PlottingTask task, int amount) {
        task.comment("image_posterize");
        task.getPlottingImage().filter(PConstants.POSTERIZE, amount); //POSTERIZE
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageBlur(PlottingTask task, int amount) {
        task.comment("image_blur");
        task.getPlottingImage().filter(PConstants.BLUR, amount); //BLUR
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageErode(PlottingTask task) {
        task.comment("image_erode");
        task.getPlottingImage().filter(PConstants.ERODE); //ERODE
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageDilate(PlottingTask task) {
        task.comment("image_dilate");
        task.getPlottingImage().filter(PConstants.DILATE); //DILATE
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static PImage imageRotate(PImage img) {
        if (img.width > img.height) {
            PImage img2 = app.createImage(img.height, img.width, PConstants.RGB);
            img.loadPixels();
            for (int x=1; x<img.width; x++) {
                for (int y=1; y<img.height; y++) {
                    int loc1 = x + y*img.width;
                    int loc2 = y + (img.width - x) * img2.width;
                    img2.pixels[loc2] = img.pixels[loc1];
                }
            }
            app.updatePixels();
            //GCodeHelper.gcode_comment("image_rotate: rotated 90 degrees to fit machines sweet spot");
            return img2;
        } else {
            //GCodeHelper.gcode_comment("image_rotate: no rotation necessary");
            return img;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void lightenOnePixel(PlottingTask task, int adjustbrightness, int x, int y) {
        int loc = (y)*task.getPlottingImage().width + x;
        float r = app.brightness (task.getPlottingImage().pixels[loc]);
        //r += adjustbrightness;
        r += adjustbrightness + app.random(0, 0.01F);
        r = app.constrain(r,0,255);
        int c = app.color(r);
        task.getPlottingImage().pixels[loc] = c;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageScale(PlottingTask task, int new_width) {
        if (task.getPlottingImage().width != new_width) {
            task.comment("image_scale, old size: " + task.getPlottingImage().width + " by " + task.getPlottingImage().height + "     ratio: " + (float)task.getPlottingImage().width / (float)task.getPlottingImage().height);
            task.getPlottingImage().resize(new_width, 0);
            task.comment("image_scale, new size: " + task.getPlottingImage().width + " by " + task.getPlottingImage().height + "     ratio: " + (float)task.getPlottingImage().width / (float)task.getPlottingImage().height);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static float avgImageBrightness(PlottingTask task) {
        float b = 0.0F;

        for (int p = 0; p < task.getPlottingImage().width * task.getPlottingImage().height; p++) {
            b += app.brightness(task.getPlottingImage().pixels[p]);
        }

        return(b / (task.getPlottingImage().width * task.getPlottingImage().height));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageCrop(PlottingTask task) {
        // This will center crop to the desired image size image_size_x and image_size_y

        float desired_ratio = app.getDrawingAreaWidthMM() / app.getDrawingAreaHeightMM();
        float current_ratio = (float)task.getPlottingImage().width / (float)task.getPlottingImage().height;

        if(desired_ratio == current_ratio){
            task.comment("image_crop image matches drawing area ratio " + desired_ratio);
            return;
        }

        task.comment("image_crop desired ratio of " + desired_ratio);
        task.comment("image_crop old size: " + task.getPlottingImage().width + " by " + task.getPlottingImage().height + "     ratio: " + current_ratio);

        PImage img2;
        if (current_ratio < desired_ratio) {
            int desired_x = task.getPlottingImage().width;
            int desired_y = (int)(task.getPlottingImage().width / desired_ratio);

            int half_y = (task.getPlottingImage().height - desired_y) / 2;
            img2 = app.createImage(desired_x, desired_y, 1);
            img2.copy(task.getPlottingImage(), 0, half_y, desired_x, desired_y, 0, 0, desired_x, desired_y);
        } else {
            int desired_x = (int)(task.getPlottingImage().height * desired_ratio);
            int desired_y = task.getPlottingImage().height;

            int half_x = (task.getPlottingImage().width - desired_x) / 2;
            img2 = app.createImage(desired_x, desired_y, 1);
            img2.copy(task.getPlottingImage(), half_x, 0, desired_x, desired_y, 0, 0, desired_x, desired_y);
        }

        task.img_plotting = img2;
        task.comment("image_crop new size: " + task.getPlottingImage().width + " by " + task.getPlottingImage().height + "     ratio: " + (float)task.getPlottingImage().width / (float)task.getPlottingImage().height);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    /**A quick and dirty way of softening the edges of your drawing.
     * Look in the boarders directory for some examples.
     * Ideally, the boarder will have similar dimensions as the image to be drawn.
     * For far more control, just edit your input image directly.
     * Most of the examples are pretty heavy handed so you can "shrink" them a few pixels as desired.
     * It does not matter if you use a transparant background or just white.  JPEG or PNG, it's all good.
     * @param task currentPlotting Task
     * @param fname Name of boarder file.
     * @param shrink Number of pixels to pull the boarder away, 0 for no change.
     * @param blur Guassian blur the boarder, 0 for no blur, 10+ for a lot.
     */
    public static void addImageBorder(PlottingTask task, String fname, int shrink, int blur) {

        //PImage boarder = createImage(app.img.getPlottingImage().width+(shrink*2), app.img.getPlottingImage().height+(shrink*2), RGB);
        PImage temp_boarder = app.loadImage("border/" + fname);
        temp_boarder.resize(task.getPlottingImage().width, task.getPlottingImage().height);
        temp_boarder.filter(PConstants.GRAY);
        temp_boarder.filter(PConstants.INVERT);
        temp_boarder.filter(PConstants.BLUR, blur);

        //boarder.copy(temp_boarder, 0, 0, temp_boarder.width, temp_boarder.height, 0, 0, boarder.width, boarder.height);
        task.getPlottingImage().blend(temp_boarder, shrink, shrink, task.getPlottingImage().width, task.getPlottingImage().height,  0, 0, task.getPlottingImage().width, task.getPlottingImage().height, PConstants.ADD);
        task.comment("image_border: " + fname + "   " + shrink + "   " + blur);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Subtle unsharp matrix
     // Source:  https://www.taylorpetrick.com/blog/post/convolution-part3
     * @param task current task, to comment image changes to
     * @param img the image to change
     * @param amount scale unsharp matrix
     */
    public static void imageUnsharpen(PlottingTask task, PImage img, int amount) {
        //
        float[][] matrix = { { -0.00391F, -0.01563F, -0.02344F, -0.01563F, -0.00391F },
                { -0.01563F, -0.06250F, -0.09375F, -0.06250F, -0.01563F },
                { -0.02344F, -0.09375F,  1.85980F, -0.09375F, -0.02344F },
                { -0.01563F, -0.06250F, -0.09375F, -0.06250F, -0.01563F },
                { -0.00391F, -0.01563F, -0.02344F, -0.01563F, -0.00391F } };


        matrix = scaleMatrix(matrix, amount);
        matrix = normalizeMatrix(matrix);

        imageConvolution(img, matrix, 1.0F, 0.0F);
        task.comment("image_unsharpen: " + amount);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageBlurr(PImage img) {
        // Basic blur matrix

        float[][] matrix = { { 1, 1, 1 },
                { 1, 1, 1 },
                { 1, 1, 1 } };

        matrix = normalizeMatrix(matrix);
        imageConvolution(img, matrix, 1, 0);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageSharpen(PImage img) {
        // Simple sharpen matrix

        float[][] matrix = { {  0, -1,  0 },
                { -1,  5, -1 },
                {  0, -1,  0 } };

        //print_matrix(matrix);
        imageConvolution(img, matrix, 1, 0);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageEmboss(PImage img) {
        float[][] matrix = { { -2, -1,  0 },
                { -1,  1,  1 },
                {  0,  1,  2 } };

        imageConvolution(img, matrix, 1, 0);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageEdgeDetect(PImage img) {
        // Edge detect
        float[][] matrix = { {  0,  1,  0 },
                {  1, -4,  1 },
                {  0,  1,  0 } };

        imageConvolution(img, matrix, 1, 0);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageMotionBlur(PImage img) {
        // Motion Blur
        // http://lodev.org/cgtutor/filtering.html

        float[][] matrix = { {  1, 0, 0, 0, 0, 0, 0, 0, 0 },
                {  0, 1, 0, 0, 0, 0, 0, 0, 0 },
                {  0, 0, 1, 0, 0, 0, 0, 0, 0 },
                {  0, 0, 0, 1, 0, 0, 0, 0, 0 },
                {  0, 0, 0, 0, 1, 0, 0, 0, 0 },
                {  0, 0, 0, 0, 0, 1, 0, 0, 0 },
                {  0, 0, 0, 0, 0, 0, 1, 0, 0 },
                {  0, 0, 0, 0, 0, 0, 0, 1, 0 },
                {  0, 0, 0, 0, 0, 0, 0, 0, 1 } };

        matrix = normalizeMatrix(matrix);
        imageConvolution(img, matrix, 1, 0);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageOutline(PImage img) {
        // Outline (5x5)
        // https://www.jmicrovision.com/help/v125/tools/classicfilterop.htm

        float[][] matrix = { { 1,  1,   1,  1,  1 },
                { 1,  0,   0,  0,  1 },
                { 1,  0, -16,  0,  1 },
                { 1,  0,   0,  0,  1 },
                { 1,  1,   1,  1,  1 } };

        //matrix = normalize_matrix(matrix);
        imageConvolution(img, matrix, 1, 0);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageSobel(PImage img, float factor, float bias) {

        // Looks like some kind of inverting edge detection
        //float[][] matrix = { { -1, -1, -1 },
        //                     { -1,  8, -1 },
        //                     { -1, -1, -1 } };

        //float[][] matrix = { {  1,  2,   0,  -2,  -1 },
        //                     {  4,  8,   0,  -8,  -4 },
        //                     {  6, 12,   0, -12,  -6 },
        //                     {  4,  8,   0,  -8,  -4 },
        //                     {  1,  2,   0,  -2,  -1 } };

        // Sobel 3x3 X
        float[][] matrixX = { { -1,  0,  1 },
                { -2,  0,  2 },
                { -1,  0,  1 } };

        // Sobel 3x3 Y
        float[][] matrixY = { { -1, -2, -1 },
                {  0,  0,  0 },
                {  1,  2,  1 } };

        imageConvolution(img, matrixX, factor, bias);
        imageConvolution(img, matrixY, factor, bias);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void imageConvolution(PImage img, float[][] matrix, float factor, float bias) {
        // What about edge pixels?  Ignoring (maxrixsize-1)/2 pixels on the edges?

        int n = matrix.length;      // matrix rows
        int m = matrix[0].length;   // matrix columns

        //print_matrix(matrix);

        PImage simg = app.createImage(img.width, img.height, RGB);
        simg.copy(img, 0, 0, img.width, img.height, 0, 0, simg.width, simg.height);
        int matrixsize = matrix.length;

        for (int x = 0; x < simg.width; x++) {
            for (int y = 0; y < simg.height; y++ ) {
                int c = convolution(x, y, matrix, matrixsize, simg, factor, bias);
                int loc = x + y*simg.width;
                img.pixels[loc] = c;
            }
        }
        app.updatePixels();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    // Source:  https://py.processing.org/tutorials/pixels/
    // By: Daniel Shiffman
    // Factor & bias added by SCC

    public static int convolution(int x, int y, float[][] matrix, int matrixsize, PImage img, float factor, float bias) {
        float rtotal = 0.0F;
        float gtotal = 0.0F;
        float btotal = 0.0F;
        int offset = matrixsize / 2;

        // Loop through convolution matrix
        for (int i = 0; i < matrixsize; i++) {
            for (int j= 0; j < matrixsize; j++) {
                // What pixel are we testing
                int xloc = x+i-offset;
                int yloc = y+j-offset;
                int loc = xloc + img.width*yloc;
                // Make sure we have not walked off the edge of the pixel array
                loc = constrain(loc,0,img.pixels.length-1);
                // Calculate the convolution
                // We sum all the neighboring pixels multiplied by the values in the convolution matrix.
                rtotal += (app.red(img.pixels[loc]) * matrix[i][j]);
                gtotal += (app.green(img.pixels[loc]) * matrix[i][j]);
                btotal += (app.blue(img.pixels[loc]) * matrix[i][j]);
            }
        }

        // Added factor and bias
        rtotal = (rtotal * factor) + bias;
        gtotal = (gtotal * factor) + bias;
        btotal = (btotal * factor) + bias;

        // Make sure RGB is within range
        rtotal = constrain(rtotal,0,255);
        gtotal = constrain(gtotal,0,255);
        btotal = constrain(btotal,0,255);
        // Return the resulting color
        return app.color(rtotal,gtotal,btotal);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static float [][] multiplyMatrix(float[][] matrixA, float[][] matrixB) {
        // Source:  https://en.wikipedia.org/wiki/Matrix_multiplication_algorithm
        // Test:    http://www.calcul.com/show/calculator/matrix-multiplication_;2;3;3;5

        int n = matrixA.length;      // matrixA rows
        int m = matrixA[0].length;   // matrixA columns
        int p = matrixB[0].length;

        float[][] matrixC;
        matrixC = new float[n][p];

        for (int i=0; i<n; i++) {
            for (int j=0; j<p; j++) {
                for (int k=0; k<m; k++) {
                    matrixC[i][j] = matrixC[i][j] + matrixA[i][k] * matrixB[k][j];
                }
            }
        }

        //print_matrix(matrix);
        return matrixC;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static float [][] normalizeMatrix(float[][] matrix) {
        // Source:  https://www.taylorpetrick.com/blog/post/convolution-part2
        // The resulting matrix is the same size as the original, but the output range will be constrained
        // between 0.0 and 1.0.  Useful for keeping brightness the same.
        // Do not use on a maxtix that sums to zero, such as sobel.

        int n = matrix.length;      // rows
        int m = matrix[0].length;   // columns
        float sum = 0;

        for (int i=0; i<n; i++) {
            for (int j=0; j<m; j++) {
                sum += matrix[i][j];
            }
        }

        for (int i=0; i<n; i++) {
            for (int j=0; j<m; j++) {
                matrix[i][j] = matrix[i][j] / abs(sum);
            }
        }

        return matrix;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static float [][] scaleMatrix(float[][] matrix, int scale) {
        int n = matrix.length;      // rows
        int p = matrix[0].length;   // columns
        float sum = 0;

        float [][] nmatrix = new float[n*scale][p*scale];

        for (int i=0; i<n; i++){
            for (int j=0; j<p; j++){
                for (int si=0; si<scale; si++){
                    for (int sj=0; sj<scale; sj++){
                        //println(si, sj);
                        int a1 = (i*scale)+si;
                        int a2 = (j*scale)+sj;
                        float a3 = matrix[i][j];
                        //println( a1 + ", " + a2 + " = " + a3 );
                        //nmatrix[(i*scale)+si][(j*scale)+sj] = matrix[i][j];
                        nmatrix[a1][a2] = a3;
                    }
                }
            }
            //println();
        }
        //println("scale_matrix: " + scale);
        return nmatrix;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    public static void printMatrix(float[][] matrix) {
        int n = matrix.length;      // rows
        int p = matrix[0].length;   // columns
        float sum = 0;

        for (int i=0; i<n; i++){
            for (int j=0; j<p; j++){
                sum += matrix[i][j];
                println("%10.5f ",  matrix[i][j]);
            }
            println();
        }
        println("Sum: ", sum);
    }
}
