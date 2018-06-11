package com.felix.glcamera;

import com.felix.glcamera.gles.Texture2dProgram;

/**
 * Created by Felix on 2018/6/11 0011.
 */
public enum FilterType {
    FILTER_NONE, FILTER_NORMAL, FILTER_BLACK_WHITE, FILTER_BLUR, FILTER_SHARPEN, FILTER_EDGE_DETECT, FILTER_EMBOSS;

    public static class FilterInfo {
        public Texture2dProgram.ProgramType programType;
        public float[] kernel;
        public float colorAdj;

        public FilterInfo(Texture2dProgram.ProgramType programType, float[] kernel, float colorAdj) {
            this.programType = programType;
            this.kernel = kernel;
            this.colorAdj = colorAdj;
        }
    }

    public static FilterInfo getFilterInfo(FilterType filterType) {
        Texture2dProgram.ProgramType programType;
        float[] kernel = null;
        float colorAdj = 0.0f;
        switch (filterType) {
            case FILTER_NORMAL:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT;
                break;
            case FILTER_BLACK_WHITE:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_BW;
                break;
            case FILTER_BLUR:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[]{
                        1f / 16f, 2f / 16f, 1f / 16f,
                        2f / 16f, 4f / 16f, 2f / 16f,
                        1f / 16f, 2f / 16f, 1f / 16f};
                break;
            case FILTER_SHARPEN:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[]{
                        0f, -1f, 0f,
                        -1f, 5f, -1f,
                        0f, -1f, 0f};
                break;
            case FILTER_EDGE_DETECT:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[]{
                        -1f, -1f, -1f,
                        -1f, 8f, -1f,
                        -1f, -1f, -1f};
                break;
            case FILTER_EMBOSS:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[]{
                        2f, 0f, 0f,
                        0f, -1f, 0f,
                        0f, 0f, -1f};
                colorAdj = 0.5f;
                break;
            case FILTER_NONE:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT;
                break;
            default:
                throw new RuntimeException("Unknown filter mode ");
        }
        return new FilterInfo(programType, kernel, colorAdj);
    }


}
