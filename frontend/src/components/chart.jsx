import { parseDate } from '../lib/format.js'

function niceStep(max, count) {
  const rough = Math.max(max, 1) / (count - 1)
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const norm = rough / magnitude
  const nice = [1, 2, 2.5, 5, 10].find((s) => s >= norm) ?? 10
  return nice * magnitude
}

export function ResponseTimeChart({ points, height = 160 }) {
  const series = points
    .filter((p) => p.responseTime !== null && p.responseTime !== undefined)
    .map((p, i, all) => ({ t: parseDate(p.checkedAt)?.getTime() ?? i, v: p.responseTime, raw: p }))
    .sort((a, b) => a.t - b.t)

  if (series.length === 0) return null

  const width = 640
  const padLeft = 56
  const padRight = 16
  const padTop = 14
  const padBottom = 26
  const plotW = width - padLeft - padRight
  const plotH = height - padTop - padBottom

  const vmax = Math.max(...series.map((s) => s.v))
  const step = niceStep(vmax, 5)
  const ticks = [0]
  while (ticks[ticks.length - 1] < vmax) ticks.push(ticks[ticks.length - 1] + step)
  const maxY = ticks[ticks.length - 1]
  if (maxY < vmax) ticks.push(maxY + step)
  const top = ticks[ticks.length - 1]
  const x = (i) => padLeft + (series.length === 1 ? plotW / 2 : (i / (series.length - 1)) * plotW)
  const y = (v) => padTop + plotH - (v / top) * plotH

  const line = series.map((s, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(s.v).toFixed(1)}`).join(' ')
  const area = `${line} L${x(series.length - 1).toFixed(1)},${padTop + plotH} L${x(0).toFixed(1)},${padTop + plotH} Z`

  const xLabelEvery = Math.max(1, Math.ceil(series.length / 5))
  const xLabels = series
    .map((s, i) => ({ s, i }))
    .filter(({ i }) => i % xLabelEvery === 0 || i === series.length - 1)

  return (
    <svg
      className="chart"
      viewBox={`0 0 ${width} ${height}`}
      role="img"
      aria-label="Response time of recent checks"
      preserveAspectRatio="none"
    >
      {ticks.map((t) => (
        <g key={t}>
          <line x1={padLeft} x2={width - padRight} y1={y(t)} y2={y(t)} className="chart-grid" />
          <text x={padLeft - 8} y={y(t) + 4} className="chart-tick" textAnchor="end">
            {t >= 1000 ? `${(t / 1000).toFixed(1)}s` : `${t}ms`}
          </text>
        </g>
      ))}
      {series.length > 1 && <path d={area} className="chart-area" />}
      <path d={line} className="chart-line" />
      {xLabels.map(({ s, i }) => (
        <text key={`x-${i}`} x={x(i)} y={height - 6} className="chart-tick" textAnchor={i === 0 ? 'start' : i === series.length - 1 ? 'end' : 'middle'}>
          {fmtShortTime(s.raw.checkedAt)}
        </text>
      ))}
    </svg>
  )
}

function fmtShortTime(value) {
  const date = parseDate(value)
  if (!date) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}
