package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeRailSizeTest {
    @Test public void focusedCoverCanExpandInAnyOfTheThreeSlots() {
        int first=100,wide=798,small=337;
        for (int expanded=first;expanded<first+3;expanded++) {
            assertEquals(first,RecommendationRail.windowStart(expanded,first));
            assertEquals(0,RecommendationRail.slotLeft(first,first,expanded,wide,small));
            int end=0;
            for (int at=first;at<first+3;at++) {
                assertEquals(end,RecommendationRail.slotLeft(at,first,expanded,wide,small));
                end+=at==expanded?wide:small;
            }
            assertEquals(wide+2*small,end);
            assertEquals(end,RecommendationRail.slotLeft(first+3,first,expanded,wide,small));
        }
        assertEquals(first+1,RecommendationRail.windowStart(first+3,first));
        assertEquals(first-1,RecommendationRail.windowStart(first-1,first));
        assertEquals(-small,RecommendationRail.slotLeft(first-1,first,first+1,wide,small));
    }
    @Test public void bothCachedFormatsFollowTheRequestedFade() {
        assertEquals(0,RecommendationRail.availableBlend(0,true,true),0);
        assertEquals(.5f,RecommendationRail.availableBlend(.5f,true,true),0);
        assertEquals(1,RecommendationRail.availableBlend(1,true,true),0);
    }
    @Test public void missingFormatKeepsTheAvailableCoverVisible() {
        assertEquals(0,RecommendationRail.availableBlend(1,false,true),0);
        assertEquals(1,RecommendationRail.availableBlend(0,true,false),0);
        assertEquals(0,RecommendationRail.availableBlend(0,false,false),0);
        assertEquals(1,RecommendationRail.availableBlend(1,false,false),0);
    }
    @Test public void aLateLayerStartsItsFadeAtTheCurrentOpacity() {
        float progress=.7f,visible=.2f;
        float from=RecommendationRail.rebasedStart(visible,visible,1,progress);
        assertEquals(visible,from+(1-from)*progress,.0001f);
        assertEquals(1,from+(1-from),.0001f);
    }
    @Test public void firstCoverUses45PercentAndLeavesNextCoverVisible() {
        for (int available:new int[]{720,1720,2400}) for (int gap:new int[]{12,24}) for (int first:new int[]{0,7,1073741760}) {
            int wide=RecommendationRail.cardWidth(available,gap,first,first),small=RecommendationRail.cardWidth(available,gap,first+1,first);
            assertEquals(Math.round(available*.45f),wide-gap);
            assertEquals(small,RecommendationRail.cardWidth(available,gap,first+2,first));
            int preview=available-wide-2*small;
            assertTrue(preview>gap/2);
            assertEquals(Math.max(gap,Math.round(available*.03f)),preview,1);
            assertEquals(small,RecommendationRail.cardWidth(available,gap,first+3,first));
            assertEquals(wide,RecommendationRail.cardWidth(available,gap,first+1,first+1));
            assertEquals(small,RecommendationRail.cardWidth(available,gap,first+2,first+1));
            assertEquals(small,RecommendationRail.cardWidth(available,gap,first-1,first));
        }
    }
    @Test public void allHomeCardsShareTheSameHeightWithFixedFormatWidths() {
        int[] horizontal=RecommendationRail.artworkSize(774,470,true),vertical=RecommendationRail.artworkSize(411,470,false);
        assertArrayEquals(new int[]{774,470},horizontal);
        assertArrayEquals(new int[]{313,470},vertical);
        assertEquals(horizontal[1],vertical[1]);
        assertArrayEquals(vertical,RecommendationRail.artworkSize(411,470,false));
    }

    @Test public void compactPortraitSlotsLeavePartOfTheFourthCardVisible() {
        int available=1720,gap=24,height=470,first=100;
        int wide=RecommendationRail.slotWidth(available,gap,first,first,height),small=RecommendationRail.slotWidth(available,gap,first+1,first,height);
        assertEquals(Math.round(available*.45f),wide-gap);
        assertEquals(small,RecommendationRail.slotWidth(available,gap,first+2,first,height));
        int preview=available-wide-2*small;
        assertTrue(preview>gap&&preview<small);
    }
    @Test public void artworkFadeStartsAndFinishesWithoutAbruptSteps() {
        assertEquals(0f,HomeArtworkFade.fraction(0),0);
        assertEquals(.5f,HomeArtworkFade.fraction(HomeArtworkFade.DURATION_MS/2),.001f);
        assertEquals(1f,HomeArtworkFade.fraction(HomeArtworkFade.DURATION_MS),0);
    }
    @Test public void lateArtworkCanChangeTheDestinationWithoutJumpingAtItsCurrentFrame() {
        for (float progress:new float[]{0,.1f,.5f,.9f}) {
            float before=470+(435-470)*progress;
            float start=RecommendationRail.rebasedStart(470,435,447,progress);
            assertEquals(before,start+(447-start)*progress,.001f);
            assertEquals(447,start+(447-start),.001f);
        }
        assertEquals(447,RecommendationRail.rebasedStart(470,435,447,1),0);
    }
    @Test public void reversingTheBlendKeepsTheSameVisibleColors() {
        for (float mix:new float[]{0,.1f,.4f,.8f,1}) {
            float forwardRed=1-mix,forwardBlue=mix;
            float reverse=HomeArtworkFade.blend(1-mix,0);
            assertEquals(forwardRed,reverse,.0001f);
            assertEquals(forwardBlue,1-reverse,.0001f);
            assertEquals(1,HomeArtworkFade.blend(1-mix,1),0);
        }
    }
    @Test public void portraitUsesInitialCoverScaleAndLandscapeUsesFinalWholeImageScale() {
        assertEquals(1.6f,HomeArtworkFade.artworkScale(150,240,100,150,true),.001f);
        assertEquals(1,HomeArtworkFade.artworkScale(400,220,400,200,false),.001f);
        assertEquals(4,HomeArtworkFade.artworkScale(400,220,100,150,true),.001f);
        assertEquals(220/150f,HomeArtworkFade.artworkScale(400,220,100,150,false),.001f);
    }

}
