package fr.luteal.core.model.report

object MetricSeriesSmoother {
    const val DEFAULT_WINDOW_DAYS = 7

    fun movingAverage(series: List<DailyMetricPoint>, windowDays: Int = DEFAULT_WINDOW_DAYS): List<DailyMetricPoint> {
        if (series.isEmpty() || windowDays < 1) return emptyList()
        val sorted = series.sortedBy { it.date }
        return sorted.mapIndexed { index, point ->
            val start = maxOf(0, index - windowDays + 1)
            val slice = sorted.subList(start, index + 1)
            val avg = slice.map { it.value }.average()
            DailyMetricPoint(point.date, avg)
        }
    }
}
