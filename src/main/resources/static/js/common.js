/* Shared helpers (formatting, API calls) and the dataset buttons: Upload CSV, Load Sample Data, Delete Dataset. */
const App = (() => {
  const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
  const currencyCompact = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', notation: 'compact', maximumFractionDigits: 1 });
  const integer = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });
  const monthFmt = new Intl.DateTimeFormat('en-US', { month: 'short', year: 'numeric', timeZone: 'UTC' });
  const dateFmt = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' });

  const fmt = {
    money: v => v == null ? 'N/A' : currency.format(Number(v)),
    moneyCompact: v => v == null ? 'N/A' : currencyCompact.format(Number(v)),
    int: v => v == null ? 'N/A' : integer.format(Number(v)),
    pct: v => v == null ? 'N/A' : `${Number(v).toFixed(1)}%`,
    signedPct: v => v == null ? 'N/A' : `${Number(v) > 0 ? '+' : ''}${Number(v).toFixed(1)}%`,
    month: iso => iso ? monthFmt.format(new Date(iso + 'T00:00:00Z')) : '',
    date: iso => iso ? dateFmt.format(new Date(iso + 'T00:00:00Z')) : '',
  };

  /** Builds a query string, skipping empty values. */
  function query(params) {
    const q = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => { if (v !== null && v !== undefined && v !== '') q.set(k, v); });
    const s = q.toString();
    return s ? `?${s}` : '';
  }

  /** fetch() wrapper that turns RFC 9457 problem responses into readable errors. */
  async function api(url, options = {}) {
    const res = await fetch(url, { headers: { Accept: 'application/json' }, ...options });
    if (res.status === 204) return null;
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      const err = new Error(body.title || `Request failed (${res.status})`);
      err.details = body.errors || (body.detail ? [body.detail] : []);
      throw err;
    }
    return body;
  }

  function escapeHtml(s) {
    return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  // ---------- Dataset actions ----------
  // After a dataset is loaded or deleted, the page reloads without its query string. The server
  // then renders the page from the current database (dashboard or the two import buttons), the
  // charts and tables fetch fresh SQL results, and any filters from the old dataset are dropped.
  function reloadFromDatabase() {
    location.href = location.pathname;
  }

  function showDatasetError(error) {
    const box = document.getElementById('dataset-error');
    box.innerHTML = `<b>${escapeHtml(error.message)}</b>`
      + `<ul>${(error.details || []).map(d => `<li>${escapeHtml(d)}</li>`).join('')}</ul>`;
    box.hidden = false;
  }

  async function runImport(button, request) {
    document.querySelectorAll('.start-panel button').forEach(b => { b.disabled = true; });
    document.getElementById('dataset-error').hidden = true;
    button.textContent = 'Loading…';
    try {
      await request();
      reloadFromDatabase();
    } catch (e) {
      // The import was rejected and the database is unchanged; let the user try again.
      showDatasetError(e);
      document.querySelectorAll('.start-panel button').forEach(b => { b.disabled = false; });
      button.textContent = button.dataset.label;
    }
  }

  function setUpStartActions() {
    const uploadButton = document.getElementById('upload-csv');
    if (!uploadButton) return; // a dataset is loaded, so the start actions aren't on the page
    const sampleButton = document.getElementById('load-sample');
    const fileInput = document.getElementById('csv-file');
    uploadButton.dataset.label = uploadButton.textContent;
    sampleButton.dataset.label = sampleButton.textContent;

    sampleButton.addEventListener('click', () =>
      runImport(sampleButton, () => api('/api/data/sample', { method: 'POST' })));

    // "Upload CSV" opens the file chooser; choosing a file uploads it straight away.
    uploadButton.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', () => {
      const file = fileInput.files[0];
      if (!file) return;
      const form = new FormData();
      form.append('file', file);
      fileInput.value = ''; // lets the user pick the same file again after fixing it
      runImport(uploadButton, () => api('/api/data/upload', { method: 'POST', body: form }));
    });
  }

  function setUpDeleteButton() {
    const deleteButton = document.getElementById('delete-dataset');
    if (!deleteButton) return;
    deleteButton.addEventListener('click', async () => {
      if (!confirm('Delete the current dataset? All stored orders and line items will be removed.')) return;
      deleteButton.disabled = true;
      try {
        await api('/api/data', { method: 'DELETE' });
        reloadFromDatabase();
      } catch (e) {
        alert(e.message);
        deleteButton.disabled = false;
      }
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    setUpStartActions();
    setUpDeleteButton();
  });

  return { fmt, query, api, escapeHtml, reloadFromDatabase };
})();
