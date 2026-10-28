package com.cleanyourtv.albumdream;

/**
 * How the three prints share the screen's width. A portrait print is limited by the screen's height and a landscape
 * one by its column's width, so in three equal columns a landscape photo ends up far smaller than a portrait one.
 * A landscape print therefore gets a wider column, and how much wider follows the album: the more of it is portrait,
 * the more a landscape photo needs to stand out beside the portraits around it. Pure Java.
 */
final class Spread {

    /** A landscape column's width relative to a portrait column's, with no portraits in the album. */
    static final float MIN_WEIGHT = 1.25f;
    /** ... and with nothing but portraits. */
    static final float MAX_WEIGHT = 2.0f;

    private Spread() {}

    /** From the album's mix: {@link #MIN_WEIGHT} for all landscape, {@link #MAX_WEIGHT} for all portrait. */
    static float landscapeWeight(int portraits, int landscapes) {
        int total = portraits + landscapes;
        if (total <= 0) return (MIN_WEIGHT + MAX_WEIGHT) / 2f;
        float portraitShare = portraits / (float) total;
        return MIN_WEIGHT + (MAX_WEIGHT - MIN_WEIGHT) * portraitShare;
    }

    /**
     * The column widths for the prints in a batch, adding up to {@code total}. {@code landscape[i]} is true for a
     * landscape (or square) print; a column with no print counts as a portrait one.
     */
    static float[] columnWidths(boolean[] landscape, float landscapeWeight, float total) {
        float[] weights = new float[landscape.length];
        float sum = 0;
        for (int i = 0; i < weights.length; i++) {
            weights[i] = landscape[i] ? landscapeWeight : 1f;
            sum += weights[i];
        }
        float[] widths = new float[weights.length];
        for (int i = 0; i < widths.length; i++) widths[i] = total * weights[i] / sum;
        return widths;
    }
}
