/* Transaction explorer: search, filter, sort, paginate and expand orders via /api/transactions. */
(() => {
  const { fmt, query, api, escapeHtml } = App;
  const form = document.getElementById('tx-filters');
  const body = document.getElementById('tx-body');
  const sizeSelect = document.getElementById('tx-size');
  const KEYS = ['search', 'status', 'category', 'region', 'startDate', 'endDate', 'sort'];
  if (!form) return; // the database is empty, so the explorer isn't on the page

  let page = 0;
  let totalPages = 0;
  let seq = 0;
  let searchTimer;

  function params() {
    const data = new FormData(form);
    const p = Object.fromEntries(KEYS.map(k => [k, (data.get(k) || '').trim()]));
    if (p.sort === 'DATE_DESC') p.sort = ''; // default, keeps URLs short
    return p;
  }

  function restoreFromUrl() {
    const url = new URLSearchParams(location.search);
    KEYS.forEach(k => { if (url.has(k)) form.elements[k].value = url.get(k); });
    if (!form.elements.sort.value) form.elements.sort.value = 'DATE_DESC';
    page = Math.max(0, parseInt(url.get('page') || '0', 10) || 0);
    if (url.has('size')) sizeSelect.value = url.get('size');
  }

  function updateSortButtons() {
    const sort = form.elements.sort.value;
    document.querySelectorAll('.sort-btn').forEach(btn => {
      const active = sort.startsWith(btn.dataset.sort);
      btn.classList.toggle('active', active);
      btn.querySelector('.arrow').textContent = active && sort.endsWith('ASC') ? '↑' : '↓';
      btn.closest('th').setAttribute('aria-sort', active ? (sort.endsWith('ASC') ? 'ascending' : 'descending') : 'none');
    });
  }

  function rowHtml(r) {
    return `
      <tr class="order-row" data-order="${escapeHtml(r.orderId)}" tabindex="0" aria-expanded="false">
        <td class="mono">${escapeHtml(r.orderId)}</td>
        <td>${fmt.date(r.orderDate)}</td>
        <td><button type="button" class="link-btn" data-customer="${escapeHtml(r.customerId)}" title="Show all orders for this customer">${escapeHtml(r.customerId)}</button></td>
        <td>${escapeHtml(r.region)}</td>
        <td><span class="badge ${escapeHtml(r.status)}">${escapeHtml(r.status.charAt(0) + r.status.slice(1).toLowerCase())}</span></td>
        <td>${escapeHtml(r.categories)}</td>
        <td class="num">${fmt.int(r.itemCount)}</td>
        <td class="num">${fmt.int(r.units)}</td>
        <td class="num">${Number(r.discountAmount) > 0 ? '−' + fmt.money(r.discountAmount) : '—'}</td>
        <td class="num"><b>${fmt.money(r.netAmount)}</b></td>
      </tr>`;
  }

  async function load() {
    const p = params();
    if (p.startDate && p.endDate && p.startDate > p.endDate) {
      showError('“From” date must be on or before “To” date.');
      return;
    }
    const size = sizeSelect.value;
    const full = { ...p, page: page || '', size: size === '20' ? '' : size };
    history.replaceState(null, '', location.pathname + query(full));
    document.getElementById('export-link').href = '/api/transactions/export' + query(p);
    updateSortButtons();

    const mySeq = ++seq;
    document.getElementById('tx-table-wrap').classList.add('loading');
    try {
      const data = await api('/api/transactions' + query({ ...p, page, size }));
      if (mySeq !== seq) return;
      hideError();
      totalPages = data.totalPages;
      if (page > 0 && page >= totalPages) { page = Math.max(0, totalPages - 1); return load(); }
      body.innerHTML = data.rows.length
        ? data.rows.map(rowHtml).join('')
        : '<tr><td colspan="10" class="muted" style="text-align:center;padding:28px">No orders match these filters.</td></tr>';
      const from = data.totalRows ? data.page * data.size + 1 : 0;
      const to = data.page * data.size + data.rows.length;
      document.getElementById('tx-summary').textContent =
        `Showing ${fmt.int(from)}–${fmt.int(to)} of ${fmt.int(data.totalRows)} orders`;
      document.getElementById('tx-page').textContent = totalPages ? `Page ${page + 1} of ${totalPages}` : '';
      document.getElementById('tx-prev').disabled = page === 0;
      document.getElementById('tx-next').disabled = page >= totalPages - 1;
    } catch (e) {
      showError([e.message, ...(e.details || [])].join(' — '));
    } finally {
      if (mySeq === seq) document.getElementById('tx-table-wrap').classList.remove('loading');
    }
  }

  function showError(msg) {
    const box = document.getElementById('tx-error');
    box.textContent = msg;
    box.hidden = false;
  }

  function hideError() {
    document.getElementById('tx-error').hidden = true;
  }

  async function toggleDetail(row) {
    const next = row.nextElementSibling;
    if (next && next.classList.contains('detail-row')) {
      next.remove();
      row.classList.remove('open');
      row.setAttribute('aria-expanded', 'false');
      return;
    }
    row.classList.add('open');
    row.setAttribute('aria-expanded', 'true');
    const detail = document.createElement('tr');
    detail.className = 'detail-row';
    detail.innerHTML = '<td colspan="10" class="muted">Loading line items…</td>';
    row.after(detail);
    try {
      const d = await api('/api/transactions/' + encodeURIComponent(row.dataset.order));
      detail.innerHTML = `<td colspan="10">
        <table class="mini-table" aria-label="Line items for ${escapeHtml(d.order.orderId)}">
          <thead><tr><th>Line</th><th>Product</th><th>Category</th><th class="num">Qty</th><th class="num">Unit price</th><th class="num">Discount</th><th class="num">Line total</th></tr></thead>
          <tbody>${d.items.map(i => `
            <tr>
              <td class="mono">${escapeHtml(i.lineItemId)}</td>
              <td>${escapeHtml(i.productName)}</td>
              <td>${escapeHtml(i.category)}</td>
              <td class="num">${fmt.int(i.quantity)}</td>
              <td class="num">${fmt.money(i.unitPrice)}</td>
              <td class="num">${Number(i.discountPercent) ? fmt.pct(i.discountPercent) : '—'}</td>
              <td class="num">${fmt.money(i.lineNet)}</td>
            </tr>`).join('')}
          </tbody>
          <tfoot><tr><td colspan="6" class="num"><b>Order total</b>${d.order.status !== 'COMPLETED' ? ' <span class="muted">(not counted as revenue: ' + d.order.status.toLowerCase() + ')</span>' : ''}</td><td class="num"><b>${fmt.money(d.order.netAmount)}</b></td></tr></tfoot>
        </table></td>`;
    } catch (e) {
      detail.innerHTML = `<td colspan="10" class="muted">${escapeHtml(e.message)}</td>`;
    }
  }

  // ---------- Events ----------
  form.addEventListener('change', e => {
    if (e.target.name === 'search') return;
    page = 0;
    load();
  });
  form.elements.search.addEventListener('input', () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { page = 0; load(); }, 300);
  });
  form.addEventListener('submit', e => { e.preventDefault(); page = 0; load(); });
  form.addEventListener('reset', () => setTimeout(() => { form.elements.sort.value = 'DATE_DESC'; page = 0; load(); }));
  sizeSelect.addEventListener('change', () => { page = 0; load(); });

  document.querySelectorAll('.sort-btn').forEach(btn => btn.addEventListener('click', () => {
    const current = form.elements.sort.value;
    const key = btn.dataset.sort;
    form.elements.sort.value = current === `${key}_DESC` ? `${key}_ASC` : `${key}_DESC`;
    page = 0;
    load();
  }));

  document.getElementById('tx-prev').addEventListener('click', () => { if (page > 0) { page--; load(); } });
  document.getElementById('tx-next').addEventListener('click', () => { if (page < totalPages - 1) { page++; load(); } });

  body.addEventListener('click', e => {
    const customer = e.target.closest('[data-customer]');
    if (customer) {
      form.elements.search.value = customer.dataset.customer;
      page = 0;
      load();
      return;
    }
    const row = e.target.closest('tr.order-row');
    if (row) toggleDetail(row);
  });
  body.addEventListener('keydown', e => {
    const row = e.target.closest('tr.order-row');
    if (row && e.target === row && (e.key === 'Enter' || e.key === ' ')) {
      e.preventDefault();
      toggleDetail(row);
    }
  });

  restoreFromUrl();
  load();
})();
