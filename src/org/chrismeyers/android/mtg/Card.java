
package org.chrismeyers.android.mtg;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import android.util.Log;

public class Card {
    public static final String TAG = "Card";
    public static final double CARD_AREA_MIN = 10000;
    public static final Scalar COLOR_YELLOW = new Scalar(255, 255, 0);

    public static final int RECTANGLE_NOT_FOUND_CANNY = 1;
    public static final int RECTANGLE_COULD_NOT_ROTATE = 2;
    public static final int RECTANGLE_FOUND = 3;

    public static final int IMG_PROCESS_WIDTH = 320;
    public static final int IMG_PROCESS_HEIGHT = 240;

    /**
     * Given an image, attempts to find a card in it
     * 
     * @return
     */
    static ArrayList<Integer> detect(Mat imageGray) {
        List<MatOfPoint> edgeContours = new ArrayList<MatOfPoint>();
        Mat imgCanny = ImageCanny(imageGray);
        Mat hierarchy = new Mat();
        Imgproc.findContours(imgCanny, edgeContours, hierarchy, Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE);

        Log.d(TAG, "Contour size:");
        for (MatOfPoint point : edgeContours) {

            Log.d(TAG,
                    "\tcontour size " + point.width() + "x" + point.height() + " - " + point.cols()
                            + "x" + point.rows());
        }

        return null;

    }

    static Mat ImageCanny(Mat imageGray) {
        Mat imgCanny = imageGray.clone();
        Imgproc.Canny(imageGray, imgCanny, 100, 100);
        return imgCanny;
    }

    /**
     * @param image may be modified
     * @param edgeContours
     * @param hierarchy
     * @param mode
     * @param method
     * @return
     */
    static Mat findAndDrawContours(Mat image, List<MatOfPoint> edgeContours) {
        int method = Imgproc.CHAIN_APPROX_SIMPLE;
        int mode = Imgproc.RETR_LIST;
        Mat img = image.clone();
        int contourIdx = -1; // draw all contours
        Mat hierarchy = new Mat();

        /*
         * The canny filter finds the outlines.
         */
        Mat imgCanny = ImageCanny(image);
        /*
         * Finding contours amounts to finding "outside" edges. So of the set of
         * edges found by the canny filter, further reduce them
         */
        Imgproc.findContours(imgCanny, edgeContours, hierarchy, mode, method);
        Scalar color = new Scalar(255, 255, 0);
        Imgproc.drawContours(img, edgeContours, contourIdx, color);
        return img;
    }

    static public class ContourArea {
        MatOfPoint points;
        double area;
    };

    /**
     * Find the single largest contour by area. Optimize the algorithm by
     * skipping computing the area on contours that have rows or columns less
     * than the minimum.
     * 
     * @param points
     * @param minRows
     * @param minCols
     * @return
     */
    static ContourArea findGreatestContourByArea(final List<MatOfPoint> points, final int minRows,
            final int minCols) {
        if (points == null || points.size() == 0) {
            return null;
        }
        ContourArea countourArea = new Card.ContourArea();
        countourArea.points = points.get(0);
        countourArea.area = Imgproc.contourArea(countourArea.points);

        for (MatOfPoint matOfPoint : points) {
            if (matOfPoint.rows() < minRows) {
                continue;
            }

            if (matOfPoint.cols() < minCols) {
                continue;
            }

            double areaTmp = Imgproc.contourArea(matOfPoint);
            if (areaTmp > countourArea.area) {
                countourArea.points = matOfPoint;
                countourArea.area = areaTmp;
            }
        }
        return countourArea;
    }

    /**
     * Note the logic here is flawed. We can't just look at the row count Find
     * the single largest contour by area. More accurate results obtained by
     * discarding rows that are larger than the image size.
     * 
     * @param points
     * @param minRows
     * @param minCols
     * @return
     */
    static MatOfPoint findGreatestContourByRow(List<MatOfPoint> points, int maxRows) {
        if (points == null || points.size() == 0) {
            return null;
        }
        MatOfPoint greatestPoint = points.get(0);
        int greatestRows = greatestPoint.rows();

        for (MatOfPoint matOfPoint : points) {
            int rows = matOfPoint.rows();
            if (rows > maxRows) {
                continue;
            }
            if (rows > greatestRows) {
                greatestPoint = matOfPoint;
            }
        }
        return greatestPoint;
    }

    static boolean resizeIfNeeded(Mat img, int desiredWidth, int desiredHeight) {
        Size size = img.size();
        Size desiredSize = new Size(desiredWidth, desiredHeight);
        if (size.width != desiredWidth || size.height != desiredHeight) {
            Imgproc.resize(img, img, desiredSize);
            return true;
        }
        return false;
    }

    /**
     * Will zone in on the largest rectangle
     * 
     * @param imageGray
     * @return
     */
    static int findLargestRectangle(Mat imgFinal) {
        // Mat imgFinal = new Mat(240, 320, imageGray.type()), imgCanny = null;
        Mat imgCanny = null;
        List<MatOfPoint> edgeContours = new ArrayList<MatOfPoint>();
        Card.ContourArea greatestArea = null;
        Rect rect = null;

        resizeIfNeeded(imgFinal, IMG_PROCESS_WIDTH, IMG_PROCESS_HEIGHT);
        imgCanny = ImageCanny(imgFinal);

        Imgproc.findContours(imgCanny, edgeContours, new Mat(), Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE);
        if (edgeContours.size() == 0) {
            imgCanny.copyTo(imgFinal);
            return RECTANGLE_NOT_FOUND_CANNY;
        }

        // find the largest contour box
        greatestArea = findGreatestContourByArea(edgeContours, 120, 0);
        if (greatestArea.points == null || greatestArea.area < CARD_AREA_MIN) {
            imgCanny.copyTo(imgFinal);
            return RECTANGLE_NOT_FOUND_CANNY;
        }

        /* Draw the largest contour */
        // ArrayList<MatOfPoint> tmp = new ArrayList<MatOfPoint>();
        // tmp.add(greatestArea.points);
        // Imgproc.drawContours(imgFinal, tmp, -1, COLOR_YELLOW);

        // draw a box around the largest contours
        rect = Imgproc.boundingRect(greatestArea.points);

        /* Determine rotate amount */

        Point[] corners = null;

        double topSlope = (corners[1].y - corners[0].y) / (corners[1].x - corners[0].x);
        double angle = Math.atan(topSlope) * 180 / Math.PI;

        Log.d("ROTATE", "slope " + topSlope + " angle " + angle);

        /*
         * Log.d("ROTATE", "TL " + topLeftPoint.x + "," + topLeftPoint.y +
         * " TR " + topRightPoint.x + "," + topRightPoint.y + " Slope " +
         * topSlope + " Angle " + Math.atan(topSlope));
         */

        // Hopefully this doesn't happen
        // if (topRightPoint == null) {
        // return RECTANGLE_COULD_NOT_ROTATE;
        // }

        // for (int i=0; i < greatestArea.points.rows(); ++i) {
        // for (int j=0; j < greatestArea.points.cols(); ++i) {
        // double[] row = greatestArea.points.get(i, j);
        // }
        // }

        Core.rectangle(imgFinal, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y
                + rect.height), COLOR_YELLOW);

        /* Rotate */
        if (angle != Double.NaN) {
            /*
             * Mat rotationMatrix = Imgproc.getRotationMatrix2D( new
             * Point(imgFinal.width() / 2, imgFinal.height() / 2), angle, 1.0);
             * Size imgFinalSize = imgFinal.size(); int maxSize = (int)
             * Math.max((double) imgFinalSize.height, (double)
             * imgFinalSize.width); Size finalSize = new Size(maxSize, maxSize);
             * Imgproc.warpAffine(imgFinal, imgFinal, rotationMatrix,
             * finalSize);
             */
        }

        // extract an image that is only in the box
        /*
         * imgFinal = imgFinal.submat(rect.y, rect.y + rect.height, rect.x,
         * rect.x + rect.width);
         */

        // orientContour(greatest);
        return RECTANGLE_FOUND;
    }

    /**
     * @param points
     * @return
     */
    static Mat orientContour(final MatOfPoint points) {
        Mat imgOriented = null;
        MatOfInt hull = new MatOfInt();

        Imgproc.convexHull(points, hull);
        return imgOriented;
    }

    static ArrayList<Integer> findLongestLines(MatOfPoint points) {
        return null;
    }

    double distance(double x1, double y1, double x2, double y2)
    {
        return Math.sqrt(Math.pow((x2 - x1), 2) + Math.pow((y2 - y1), 2));
    }

    static double distance(Point p1, Point p2)
    {
        return Math.sqrt(Math.pow((p2.x - p1.x), 2) + Math.pow((p2.y - p1.y), 2));
    }

    /**
     * Given a set of points that follow a rectangular pattern (i.e. usually
     * from a contour), return the four corner points in a clock-wise ordering
     * from top-left.
     * 
     * @param points
     * @return
     */
    static Point[] identifyRectangleCornersByMax(Point[] points) {
        Point[] cornerPoints = new Point[4];
        Point topLeft = null, topRight = null, botRight = null, botLeft = null;
        Point p1 = null, p2 = null, p3 = null, p4 = null;

        Point greatestX = new Point(0, 0);
        Point leastX = new Point(Double.MAX_VALUE, 0);
        Point greatestY = new Point(0, 0);
        Point leastY = new Point(0, Double.MAX_VALUE);

        for (int i = 0; i < points.length; ++i) {
            greatestX = points[i].x > greatestX.x ? points[i] : greatestX;
            leastX = points[i].x < leastX.x ? points[i] : leastX;
            greatestY = points[i].y > greatestY.y ? points[i] : greatestY;
            leastY = points[i].y < leastY.y ? points[i] : leastY;
        }

        ArrayList<Point> corners = new ArrayList<Point>();
        corners.add(greatestX);
        corners.add(leastX);
        corners.add(greatestY);
        corners.add(leastY);

        Log.d("CORNERS", "Candidates " + greatestX.x + " " + greatestX.y + "\n" + leastX.x + " "
                + leastX.y + "\n" + greatestY.x + " " + greatestY.y + "\n" + leastY.x + " "
                + leastY.y);

        if (corners.size() != 4) {
            Log.e("CORNERS", "We don't have 4 corners!!");
            return null;
        }

        /* Top set of points */
        // find p1
        p1 = corners.get(0);
        for (Point point : corners) {
            if (point.y < p1.y) {
                p1 = point;
            }
        }
        corners.remove(p1);

        // find p2
        p2 = corners.get(0);
        double leastDist = distance(p1, p2);
        for (Point point : corners) {
            if (distance(p1, point) < distance(p1, p2)) {
                p2 = point;
            }
        }
        corners.remove(p2);

        /* Identify top left and top right */
        /*
         * Note that the logic is safe if the points have equal x values. Safe
         * in the sense that different points will get assigned to topLeft and
         * topRight
         */
        topLeft = p1.x < p2.x ? p1 : p2;
        topRight = p2.x > p1.x ? p2 : p1;

        /* Bottom set of points */
        // corners only contains 2 points, the bottom ones
        p3 = corners.get(0);
        p4 = corners.get(1);
        botRight = p3.x > p4.x ? p3 : p4;
        botLeft = p4.x < p3.x ? p4 : p3;

        cornerPoints[0] = topLeft;
        cornerPoints[1] = topRight;
        cornerPoints[2] = botRight;
        cornerPoints[3] = botLeft;
        return cornerPoints;
    }
}