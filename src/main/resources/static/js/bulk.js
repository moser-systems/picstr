// Bulk selection: shows the #bulk-form action bar while photos are selected (checkboxes with class
// "bulk-select" that belong to the form via form="bulk-form"). Re-initialises after hx-boost navigation.
(() => {
    let update = () => {};

    const init = () => {
        const form = document.getElementById('bulk-form');
        if (!form || form.dataset.initialized) {
            return;
        }
        form.dataset.initialized = 'true';
        const boxes = () => [...document.querySelectorAll('input.bulk-select')];
        const count = form.querySelector('.bulk-count');
        const setAll = (checked) => {
            boxes().forEach((box) => { box.checked = checked; });
            update();
        };

        update = () => {
            const selected = boxes().filter((box) => box.checked).length;
            count.textContent = form.dataset.selectedText.replace('{0}', selected);
            form.classList.toggle('d-none', selected === 0);
        };

        form.querySelector('.bulk-select-all')?.addEventListener('click', () => setAll(true));
        form.querySelector('.bulk-clear')?.addEventListener('click', () => setAll(false));
        form.addEventListener('submit', (event) => {
            if (event.submitter?.value === 'archive' && !window.confirm(form.dataset.confirmArchive)) {
                event.preventDefault();
            }
        });

        const tags = form.querySelector('#bulk-tags');
        if (tags && window.TomSelect) {
            new TomSelect(tags, {create: true, createOnBlur: true, plugins: ['remove_button']});
        }
        update();
    };

    document.addEventListener('change', (event) => {
        if (event.target.matches('input.bulk-select')) {
            update();
        }
    });
    document.addEventListener('DOMContentLoaded', init);
    document.addEventListener('htmx:afterSettle', init);
})();
