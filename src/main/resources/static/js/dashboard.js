/* Dashboard: reads the filter form, calls /api/dashboard, renders KPI cards and Chart.js charts. */
(() => {
  const { fmt, query, api, escapeHtml, reloadFromDatabase } = App;
  const form = document.getElementById('filters');
  if (!form) return; // the database is empty, so the dashboard isn't on the page

  const FILTER_KEYS = ['startDate', 'endDate', 'category', 'region'];
  const charts = {};
  let requestSeq = 0;

  // ---------- Chart colors ----------
  // Kept here as plain values (instead of reading CSS variables) so the charts always get real
  // colors. If a color came back empty, Chart.js would fall back to its default blue and black.
  const CHART_COLORS = {
    text: '#f5f5f7',
    text2: '#d4d4d8',
    text3: '#a1a1aa',
    grid: '#2f3140',
    border: '#3a3b49',
    surface: '#242631',
    surface2: '#303240',
    line: '#c4b5fd',
    fill: 'rgba(167, 139, 250, 0.14)',
    bar: '#8b5cf6',
    barHover: '#ddd6fe', // lighter than every bar color, so hovering always brightens a bar
    shades: ['#7c3aed', '#a78bfa', '#6d28d9', '#c4b5fd', '#8b5cf6'],
    status: {
      COMPLETED: '#7c3aed',
      RETURNED: '#a78bfa',
      CANCELLED: '#ddd6fe',
    },
  };

  function theme() {
    return CHART_COLORS;
  }

  function applyDefaults() {
    const t = theme();
    Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
    Chart.defaults.font.size = 12;
    Chart.defaults.color = t.text3;
    Chart.defaults.borderColor = t.grid;
    Chart.defaults.maintainAspectRatio = false;
    Chart.defaults.plugins.legend.display = false;
    // Dark tooltips that match the cards
    Object.assign(Chart.defaults.plugins.tooltip, {
      backgroundColor: t.surface2, titleColor: t.text, bodyColor: t.text2,
      borderColor: t.border, borderWidth: 1,
      padding: 10, cornerRadius: 6, displayColors: false, titleFont: { weight: '600' },
    });
  }

  // A category keeps the same shade when filters change, because the shade comes from the
  // category's position in the dropdown, not from its rank in the chart.
  function categoryColor(category) {
    const shades = theme().shades;
    const names = [...form.elements.category.options].map(option => option.value).filter(Boolean);
    const position = Math.max(0, names.indexOf(category));
    return shades[position % shades.length];
  }

  const moneyAxis = { ticks: { callback: v => fmt.moneyCompact(v) }, grid: { color: () => theme().grid }, border: { display: false } };
  const plainAxis = { grid: { display: false }, border: { color: () => theme().grid } };

  // ---------- Filters <-> URL ----------
  function readForm() {
    const data = new FormData(form);
    return Object.fromEntries(FILTER_KEYS.map(k => [k, data.get(k) || '']));
  }

  function writeForm(values) {
    FILTER_KEYS.forEach(k => { form.elements[k].value = values[k] || ''; });
  }

  function syncUrl(values) {
    history.replaceState(null, '', location.pathname + query(values));
  }

  function describeFilters(f) {
    const parts = [];
    if (f.startDate || f.endDate) parts.push(`${f.startDate ? fmt.date(f.startDate) : 'start'} – ${f.endDate ? fmt.date(f.endDate) : 'latest'}`);
    if (f.category) parts.push(f.category);
    if (f.region) parts.push(f.region);
    const note = document.getElementById('filter-note');
    note.innerHTML = parts.length
      ? `Showing <span class="active-filters">${parts.map(p => `<span class="chip">${escapeHtml(p)}</span>`).join(' ')}</span>`
        + (f.category ? ' · Category view: only items in this category are measured; orders that contain it are counted.' : '')
      : 'Showing all data. Use the filters to focus on a period, category or region.';
  }

  // ---------- Rendering ----------
  function setKpi(name, text) {
    document.querySelector(`[data-kpi="${name}"]`).textContent = text;
  }

  function setSub(name, html) {
    document.querySelector(`[data-kpi-sub="${name}"]`).innerHTML = html;
  }

  function renderKpis(k) {
    setKpi('totalRevenue', fmt.money(k.totalRevenue));
    setKpi('completedOrders', fmt.int(k.completedOrders));
    setKpi('averageOrderValue', fmt.money(k.averageOrderValue));
    setKpi('unitsSold', fmt.int(k.unitsSold));
    setKpi('returnRatePct', fmt.pct(k.returnRatePct));
    setKpi('uniqueCustomers', fmt.int(k.uniqueCustomers));
    setSub('orders', `${fmt.int(k.totalOrders)} orders placed in total`);
    setSub('returns', `${fmt.int(k.returnedOrders)} of ${fmt.int(k.nonCancelledOrders)} non-cancelled`);

    const g = k.monthlyGrowth;
    const el = document.querySelector('[data-kpi="monthlyGrowth"]');
    el.classList.remove('delta', 'up', 'down');
    if (!g) {
      el.textContent = 'N/A';
      setSub('growth', 'No months in range');
      return;
    }
    el.textContent = fmt.signedPct(g.growthPct);
    if (g.growthPct != null) el.classList.add('delta', Number(g.growthPct) >= 0 ? 'up' : 'down');
    let sub;
    if (g.growthPct != null) {
      sub = `${fmt.month(g.month)} vs ${fmt.month(g.previousMonth)}`;
    } else if (!g.previousMonth) {
      sub = `${fmt.month(g.month)}: no prior month in range`;
    } else {
      sub = `${fmt.month(g.previousMonth)} had no revenue`;
    }
    if (g.partialMonth) sub += ' · <span title="The date filter ends before the end of this month">partial month</span>';
    setSub('growth', sub);
  }

  function upsertChart(id, config) {
    const box = document.getElementById(id).parentElement;
    const empty = !config.data.labels.length;
    box.classList.toggle('is-empty', empty);
    if (charts[id]) {
      charts[id].data = config.data;
      charts[id].options = config.options;
      charts[id].update();
    } else {
      charts[id] = new Chart(document.getElementById(id), config);
    }
  }

  function renderMonthly(rows) {
    const t = theme();
    upsertChart('chart-monthly', {
      type: 'line',
      data: {
        labels: rows.map(r => fmt.month(r.month)),
        datasets: [{
          label: 'Revenue',
          data: rows.map(r => Number(r.revenue)),
          borderColor: t.line,
          backgroundColor: t.fill, // soft purple area under the line
          fill: true,
          borderWidth: 2,
          pointRadius: 4,
          pointHoverRadius: 6,
          pointBackgroundColor: t.line,
          pointBorderColor: t.surface,
          pointBorderWidth: 2,
          tension: 0.25,
        }],
      },
      options: {
        interaction: { mode: 'index', intersect: false },
        scales: { y: { ...moneyAxis, beginAtZero: true }, x: plainAxis },
        plugins: {
          tooltip: {
            callbacks: {
              label: ctx => {
                const r = rows[ctx.dataIndex];
                return [
                  `Revenue: ${fmt.money(r.revenue)}`,
                  `Completed orders: ${fmt.int(r.completedOrders)}`,
                  `Units: ${fmt.int(r.unitsSold)}`,
                  `MoM change: ${r.growthPct == null ? 'N/A' : fmt.signedPct(r.growthPct)}`,
                ];
              },
            },
          },
        },
      },
    });
  }

  // colors can be one color for every bar, or an array with one color per bar
  function horizontalBar(id, labels, values, colors, tooltipLines) {
    const t = theme();
    upsertChart(id, {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          data: values,
          backgroundColor: colors,
          hoverBackgroundColor: t.barHover,
          borderRadius: 4,
          borderSkipped: 'start',
          maxBarThickness: 22,
        }],
      },
      options: {
        indexAxis: 'y',
        scales: { x: { ...moneyAxis, beginAtZero: true }, y: { ...plainAxis, ticks: { color: t.text2 } } },
        plugins: { tooltip: { callbacks: { label: ctx => tooltipLines(ctx.dataIndex) } } },
      },
    });
  }

  function renderCategories(rows) {
    horizontalBar('chart-category',
      rows.map(r => r.category),
      rows.map(r => Number(r.revenue)),
      rows.map(r => categoryColor(r.category)),
      i => [
        `Revenue: ${fmt.money(rows[i].revenue)} (${fmt.pct(rows[i].sharePct)})`,
        `Units: ${fmt.int(rows[i].unitsSold)}`,
        `Orders containing category: ${fmt.int(rows[i].orders)}`,
      ]);
  }

  function renderProducts(rows) {
    const max = window.innerWidth < 640 ? 18 : 28;
    const short = name => name.length > max ? name.slice(0, max - 1) + '…' : name;
    horizontalBar('chart-products',
      rows.map(r => short(r.productName)),
      rows.map(r => Number(r.revenue)),
      theme().bar,
      i => [
        rows[i].productName,
        `Category: ${rows[i].category}`,
        `Revenue: ${fmt.money(rows[i].revenue)}`,
        `Units: ${fmt.int(rows[i].unitsSold)} · Orders: ${fmt.int(rows[i].orders)}`,
      ]);
  }

  function renderRegions(rows) {
    const t = theme();
    upsertChart('chart-region', {
      type: 'bar',
      data: {
        labels: rows.map(r => r.region),
        datasets: [{
          data: rows.map(r => Number(r.revenue)),
          backgroundColor: t.shades[1], // light purple, so it reads differently from the product bars
          hoverBackgroundColor: t.barHover,
          borderRadius: 4,
          borderSkipped: 'start',
          maxBarThickness: 44,
        }],
      },
      options: {
        scales: { y: { ...moneyAxis, beginAtZero: true }, x: { ...plainAxis, ticks: { color: t.text2 } } },
        plugins: {
          tooltip: {
            callbacks: {
              label: ctx => {
                const r = rows[ctx.dataIndex];
                return [`Revenue: ${fmt.money(r.revenue)} (${fmt.pct(r.sharePct)})`, `AOV: ${fmt.money(r.averageOrderValue)}`];
              },
            },
          },
        },
      },
    });
    document.getElementById('region-table').innerHTML = rows.map(r => `
      <tr>
        <td>${escapeHtml(r.region)}</td>
        <td class="num">${fmt.money(r.revenue)}</td>
        <td class="num">${fmt.int(r.completedOrders)}</td>
        <td class="num">${fmt.money(r.averageOrderValue)}</td>
        <td class="num">${fmt.pct(r.returnRatePct)}</td>
      </tr>`).join('') || '<tr><td colspan="5" class="muted">No data</td></tr>';
  }

  function renderStatuses(rows) {
    const t = theme();
    const label = s => s.charAt(0) + s.slice(1).toLowerCase();
    upsertChart('chart-status', {
      type: 'doughnut',
      data: {
        labels: rows.map(r => `${label(r.status)} · ${fmt.pct(r.sharePct)}`),
        datasets: [{
          data: rows.map(r => r.orders),
          backgroundColor: rows.map(r => t.status[r.status]),
          borderColor: t.surface,
          borderWidth: 2,
          hoverOffset: 6,
        }],
      },
      options: {
        cutout: '62%',
        plugins: {
          legend: { display: true, position: 'bottom', labels: { color: t.text2, usePointStyle: true, pointStyle: 'circle', padding: 16 } },
          tooltip: {
            callbacks: {
              label: ctx => {
                const r = rows[ctx.dataIndex];
                return [`${label(r.status)}: ${fmt.int(r.orders)} orders (${fmt.pct(r.sharePct)})`, `Order value: ${fmt.money(r.orderValue)}`];
              },
            },
          },
        },
      },
    });
  }

  // ---------- Load ----------
  async function refresh() {
    const filters = readForm();
    const errorBox = document.getElementById('error');
    if (filters.startDate && filters.endDate && filters.startDate > filters.endDate) {
      errorBox.textContent = 'Start date must be on or before end date.';
      errorBox.hidden = false;
      return;
    }
    syncUrl(filters);
    describeFilters(filters);

    const seq = ++requestSeq;
    const main = document.getElementById('dashboard');
    main.classList.add('loading');
    try {
      const data = await api('/api/dashboard' + query(filters));
      if (seq !== requestSeq) return; // a newer request superseded this one
      if (!data.hasData) return reloadFromDatabase(); // dataset was deleted (e.g. in another tab)
      errorBox.hidden = true;
      renderKpis(data.kpis);
      renderMonthly(data.monthly);
      renderCategories(data.categories);
      renderProducts(data.topProducts);
      renderRegions(data.regions);
      renderStatuses(data.statuses);
    } catch (e) {
      errorBox.textContent = [e.message, ...(e.details || [])].join(' — ');
      errorBox.hidden = false;
    } finally {
      if (seq === requestSeq) main.classList.remove('loading');
    }
  }

  applyDefaults();
  writeForm(Object.fromEntries(new URLSearchParams(location.search)));
  // Every filter change re-requests /api/dashboard with the filter values as query parameters.
  // The backend turns each non-empty filter into a WHERE condition (see SqlConditions) and applies
  // it to every KPI and chart query, so all cards and charts always describe the same records.
  form.addEventListener('change', refresh);
  form.addEventListener('submit', e => { e.preventDefault(); refresh(); });
  form.addEventListener('reset', () => setTimeout(refresh)); // after the browser clears the inputs

  refresh();
})();
