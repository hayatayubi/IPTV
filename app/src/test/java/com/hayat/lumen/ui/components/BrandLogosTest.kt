package com.hayat.lumen.ui.components

import com.hayat.lumen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrandLogosTest {

    private fun logo(name: String) = BrandLogos.forName(name)?.logo

    @Test fun bbc() {
        assertEquals(R.drawable.logo_bbc_one, logo("UK: BBC ONE LONDON 4K ●"))
        assertEquals(R.drawable.logo_bbc_one, logo("BBC 1 Scotland HD"))
        assertEquals(R.drawable.logo_bbc_two, logo("UK| BBC 2 FHD"))
        assertEquals(R.drawable.logo_bbc_two, logo("BBC TWO"))
        assertEquals(R.drawable.logo_bbc_three, logo("BBC Three"))
        assertEquals(R.drawable.logo_bbc_four, logo("BBC4"))
        assertEquals(R.drawable.logo_bbc_news, logo("UK: BBC NEWS HD"))
        assertNull(logo("BBC Alba"))
    }

    @Test fun itv() {
        assertEquals(R.drawable.logo_itv_1, logo("UK: ITV 1 HD"))
        assertEquals(R.drawable.logo_itv_1, logo("ITV1 London"))
        assertEquals(R.drawable.logo_itv_1, logo("ITV London"))
        assertEquals(R.drawable.logo_itv_1, logo("UK: ITV"))
        assertEquals(R.drawable.logo_itv_2, logo("ITV2"))
        assertEquals(R.drawable.logo_itv_3, logo("ITV 3 HD"))
        assertEquals(R.drawable.logo_itv_4, logo("ITV4"))
        assertEquals(R.drawable.logo_itv_be, logo("ITVBe"))
        assertNull(logo("ITV Encore"))
        assertNull(logo("ITVX Comedy"))
    }

    @Test fun channel4() {
        assertEquals(R.drawable.logo_channel_4, logo("UK: CHANNEL 4 HD"))
        assertEquals(R.drawable.logo_channel_4, logo("Channel4"))
        assertEquals(R.drawable.logo_e_4, logo("E4 HD"))
        assertEquals(R.drawable.logo_more_4, logo("More4"))
        assertEquals(R.drawable.logo_film_4, logo("Film4"))
        assertNull(logo("Channel 5"))
    }

    @Test fun skySports() {
        assertEquals(R.drawable.logo_sky_sports_main_event, logo("UK: SKY SPORTS MAIN EVENT UHD"))
        assertEquals(R.drawable.logo_sky_sports_premier_league, logo("Sky Sports Premier League"))
        assertEquals(R.drawable.logo_sky_sports_premier_league, logo("SKY SPORTS PL HD"))
        assertEquals(R.drawable.logo_sky_sports_f1, logo("Sky Sports F1"))
        assertEquals(R.drawable.logo_sky_sports_football, logo("Sky Sport Football"))
        assertNull(logo("Sky Cinema Premiere"))
    }
}
