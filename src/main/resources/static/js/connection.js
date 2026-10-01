// Tells users when htmx requests (boosted navigation, auto-refresh polling, ...) fail, using a single
// toast in layout.html (#connection-toast) so repeated failures don't stack up. The next successful
// request replaces the error with a short "connection restored" message.
(() => {
    let failing = false;

    const toastApi = () => (window.bootstrap && window.bootstrap.Toast) || (window.tabler && window.tabler.Toast);

    const show = (kind, message, autohide) => {
        const toast = document.getElementById('connection-toast');
        if (!toast) {
            return;
        }
        toast.querySelector('.toast-body').textContent = message;
        toast.classList.toggle('bg-red-lt', kind === 'error');
        toast.classList.toggle('bg-green-lt', kind === 'ok');
        const Toast = toastApi();
        if (Toast) {
            // Options are fixed per instance; recreate it when switching between sticky and auto-hiding
            if (toast.dataset.autohide !== String(autohide)) {
                Toast.getInstance(toast)?.dispose();
                toast.dataset.autohide = String(autohide);
            }
            Toast.getOrCreateInstance(toast, {autohide: autohide, delay: 3000}).show();
        } else {
            toast.classList.add('show');
            if (autohide) {
                setTimeout(() => toast.classList.remove('show'), 3000);
            }
        }
    };

    const text = (key) => document.getElementById('connection-toast')?.dataset[key] || '';

    const fail = (key) => {
        failing = true;
        show('error', text(key), false);
    };

    document.addEventListener('htmx:sendError', () => fail('offline'));
    document.addEventListener('htmx:timeout', () => fail('offline'));
    document.addEventListener('htmx:responseError', (event) => {
        const status = event.detail.xhr ? event.detail.xhr.status : 0;
        fail(status === 401 || status === 403 ? 'session' : 'error');
    });
    document.addEventListener('htmx:afterRequest', (event) => {
        if (failing && event.detail.successful) {
            failing = false;
            show('ok', text('restored'), true);
        }
    });
    window.addEventListener('offline', () => fail('offline'));
    window.addEventListener('online', () => {
        if (failing) {
            failing = false;
            show('ok', text('restored'), true);
        }
    });
})();
