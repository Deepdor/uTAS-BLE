package com.example.utasble

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.utils.ColorTemplate

@Composable
fun LineChartCompose(modifier: Modifier = Modifier, chartData: List<Entry>) {
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                // Customize the chart here
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                description.isEnabled = false
                setTouchEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                axisRight.isEnabled = false
                legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            }
        },
        update = { chart ->
            val lineDataSet = LineDataSet(chartData, "Pressure vs Time")
            lineDataSet.setDrawValues(false)
            lineDataSet.colors = listOf(ColorTemplate.getHoloBlue())
            lineDataSet.valueTextColor = ColorTemplate.getHoloBlue()
            lineDataSet.lineWidth = 2f

            val lineData = LineData(lineDataSet)
            chart.data = lineData
            chart.invalidate() // Refresh the chart
        },
        modifier = modifier
    )
}
