package com.example.skydeityslash.client;

import net.minecraft.util.Mth;

/** 鸣雷神 F0-F40 时间轴的忠实移植（来自 1.20KaBlade NarukamiTimeline）。 */
final class NarukamiTimeline21 {
    static final Cue ENTRY_FRONT = cue(13.92F,14.16F,14.70F,15.26F,15.62F,16.05F,1.00F,.50F);
    static final Cue ENTRY_UPPER = cue(14.42F,14.78F,15.46F,16.08F,16.72F,17.28F,.80F,.30F);
    static final Cue ORBIT_A = cue(14.92F,15.28F,15.92F,16.48F,17.12F,17.76F,.46F,.05F);
    static final Cue ORBIT_B = cue(15.52F,15.92F,16.62F,17.18F,18.12F,18.76F,.42F,.04F);
    static final Cue ORBIT_C = cue(16.20F,16.62F,17.28F,17.92F,18.86F,19.62F,.38F,.03F);
    static final Cue RING_BACK_A = cue(15.72F,16.12F,16.78F,17.36F,18.12F,18.82F,.34F,.02F);
    static final Cue RING_BACK_B = cue(16.20F,16.58F,17.24F,17.86F,18.78F,19.50F,.30F,.02F);
    static final Cue RING_BACK_C = cue(17.00F,17.42F,18.10F,18.84F,20.35F,21.20F,.26F,.01F);
    static final Cue PIERCE_VERTICAL = cue(16.82F,17.22F,17.92F,18.36F,18.92F,20.02F,1.00F,.88F);
    static final Cue PIERCE_RISING = cue(17.56F,17.94F,18.70F,19.20F,19.86F,21.02F,.98F,.84F);
    static final Cue PIERCE_HORIZONTAL = cue(18.42F,18.84F,19.62F,20.14F,20.92F,22.18F,.92F,.74F);
    static final Cue PIERCE_FALLING = cue(19.42F,19.86F,20.62F,21.18F,21.96F,23.16F,.96F,.80F);
    static final Cue PIERCE_OFFSET = cue(20.42F,20.86F,21.58F,22.16F,22.88F,24.08F,.88F,.66F);
    static final Cue COLLAPSE_MAIN = cue(19.72F,20.12F,21.12F,21.88F,23.08F,24.68F,.92F,.70F);
    static final Cue COLLAPSE_ECHO = cue(20.68F,21.12F,21.84F,22.52F,23.52F,24.42F,.30F,.03F);
    static final Cue RESIDUAL_DIAGONAL = cue(22.08F,22.48F,23.28F,24.10F,27.18F,28.72F,.80F,.30F);
    static final Cue RESIDUAL_HOOK = cue(23.42F,23.86F,25.10F,26.05F,28.10F,29.00F,.48F,.12F);

    private NarukamiTimeline21(){}
    private static Cue cue(float a,float s,float i,float h,float r,float e,float o,float b){return new Cue(a,s,i,h,r,e,o,b);}
    static float smooth(float value,float from,float to){if(Math.abs(to-from)<1E-6F)return value>=to?1:0;float x=Mth.clamp((value-from)/(to-from),0,1);return x*x*(3-2*x);}
    static float plateau(float f,float start,float full,float fade,float end){return f<start||f>end?0:Math.min(smooth(f,start,full),1-smooth(f,fade,end));}
    static float gaussian(float value,float center,float width){float x=(value-center)/Math.max(width,.001F);return(float)Math.exp(-x*x);}
    static float envelope(Cue c,float f){return c.maxOpacity*Math.min(smooth(f,c.start,c.impact),1-smooth(f,c.release,c.end));}
    static float precursor(Cue c,float f){return c.maxOpacity*.30F*plateau(f,c.anticipate,c.start,c.impact,c.handoff);}
    static float wake(Cue c,float f){return c.maxOpacity*.28F*plateau(f,c.impact,c.handoff,c.release,c.end);}
    static float reveal(Cue c,float f){return smooth(f,c.start,c.impact);}
    static float exit(Cue c,float f){return .94F*smooth(f,c.release,c.end);}
    static Cue dominant(float frame){Cue[] cs={PIERCE_VERTICAL,PIERCE_RISING,PIERCE_HORIZONTAL,PIERCE_FALLING,PIERCE_OFFSET,COLLAPSE_MAIN,RESIDUAL_DIAGONAL};Cue best=null;float score=0;for(Cue c:cs){float s=envelope(c,frame)*(.55F+c.bloom);if(s>score){score=s;best=c;}}return best;}
    static final class Cue {final float anticipate,start,impact,handoff,release,end,maxOpacity,bloom;Cue(float a,float s,float i,float h,float r,float e,float o,float b){anticipate=a;start=s;impact=i;handoff=h;release=r;end=e;maxOpacity=o;bloom=b;}}
}