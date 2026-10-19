package fr.luteal.core.model.report

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MetricSeriesSmootherTest {

    @Test
    fun movingAverageUsesTrailingWindow() {
        val base = LocalDate.of(2026, 1, 1)
        val series = (0 until 5).map { i ->
            DailyMetricPoint(base.plusDays(i.toLong()), (i + 1).toDouble())
        }
        val smoothed = MetricSeriesSmoother.movingAverage(series, windowDays = 3)
        assertEquals(1.0, smoothed[0].value, 0.001)
        assertEquals(1.5, smoothed[1].value, 0.001)
        assertEquals(2.0, smoothed[2].value, 0.001)
        assertEquals(3.0, smoothed[3].value, 0.001)
        assertEquals(4.0, smoothed[4].value, 0.001)
    }
}
