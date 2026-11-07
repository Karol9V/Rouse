const sections = {
    productos: {
        title: 'Productos',
        subtitle: 'Administra los productos del inventario.',
        action: 'Agregar producto',
        endpoint: '/api/productos',
        idField: 'idProducto',
        route: '/productos',
        columns: [
            {label: 'ID', key: 'idProducto'},
            {label: 'PRODUCTO', key: 'nombre'},
            {label: 'CATEGORÍA', key: 'categoria'},
            {label: 'PRECIO', key: 'precioVenta', format: 'currency'},
            {label: 'STOCK', key: 'stock'},
            {label: 'ESTADO', key: 'estado', format: 'status'}
        ],
        fields: [
            {key: 'nombre', label: 'Nombre', required: true},
            {key: 'descripcion', label: 'Descripción'},
            {key: 'categoria', label: 'Categoría', required: true},
            {key: 'precioVenta', label: 'Precio de venta', type: 'number', required: true, step: 'any'},
            {key: 'costo', label: 'Costo', type: 'number', step: 'any'},
            {key: 'stock', label: 'Stock', type: 'number', step: 'any'},
            {key: 'estado', label: 'Estado', type: 'select', options: ['Disponible', 'Agotado', 'Inactivo']},
            {key: 'stockMinimo', label: 'Stock mínimo', type: 'number', step: 'any', advanced: true},
            {key: 'unidadMedida', label: 'Unidad de medida', type: 'select', options: ['unidad', 'kg', 'g', 'litro', 'ml', 'paquete'], advanced: true},
            {key: 'idProveedor', label: 'Proveedor', type: 'select', optionsEndpoint: '/api/proveedores', optionValue: 'idProveedor', optionLabel: 'nombreEmpresa', advanced: true}
        ]
    },
    ventas: {
        title: 'Ventas',
        subtitle: 'Consulta y administra las ventas registradas.',
        action: 'Agregar venta',
        endpoint: '/api/ventas',
        idField: 'idVenta',
        route: '/ventas',
        columns: [
            {label: 'VENTA', key: 'idVenta'},
            {label: 'VENDEDOR', key: 'nombreVendedor'},
            {label: 'FECHA', key: 'fechaVenta', format: 'date'},
            {label: 'MÉTODO DE PAGO', key: 'metodoPago'},
            {label: 'TOTAL', key: 'total', format: 'currency'},
            {label: 'ESTADO', key: 'estado', format: 'status'}
        ],
        fields: [
            {key: 'idUsuario', label: 'Vendedor', type: 'select', optionsEndpoint: '/api/usuarios', optionValue: 'idUsuario', optionLabel: 'nombreCompleto', required: true},
            {key: 'metodoPago', label: 'Método de pago', type: 'select', options: ['Efectivo', 'Tarjeta', 'Transferencia', 'Nequi'], required: true},
            {key: 'estado', label: 'Estado', type: 'select', options: ['Completada', 'Pendiente', 'Anulada']},
            {key: 'descuento', label: 'Descuento (opcional)', type: 'number', step: 'any', advanced: true}
        ],
        lines: {
            endpoint: '/api/productos',
            title: 'Productos vendidos'
        }
    },
    proveedores: {
        title: 'Proveedores',
        subtitle: 'Administra las empresas y contactos proveedores.',
        action: 'Agregar proveedor',
        endpoint: '/api/proveedores',
        idField: 'idProveedor',
        route: '/proveedores',
        columns: [
            {label: 'EMPRESA', key: 'nombreEmpresa'},
            {label: 'CONTACTO', key: 'nombreContacto'},
            {label: 'TELÉFONO', key: 'telefono'},
            {label: 'CORREO', key: 'correo'},
            {label: 'ESTADO', key: 'estado', format: 'status'}
        ],
        fields: [
            {key: 'nombreEmpresa', label: 'Nombre de la empresa', required: true},
            {key: 'nombreContacto', label: 'Nombre del contacto'},
            {key: 'telefono', label: 'Teléfono'},
            {key: 'correo', label: 'Correo', type: 'email'},
            {key: 'direccion', label: 'Dirección'},
            {key: 'estado', label: 'Estado', type: 'select', options: ['Activo', 'Inactivo']}
        ]
    },
    usuarios: {
        title: 'Usuarios',
        subtitle: 'Administra los usuarios del sistema.',
        action: 'Agregar usuario',
        endpoint: '/api/usuarios',
        idField: 'idUsuario',
        route: '/usuarios',
        columns: [
            {label: 'NOMBRE', key: 'nombre'},
            {label: 'APELLIDO', key: 'apellido'},
            {label: 'CORREO', key: 'correo'},
            {label: 'ROL', key: 'rol'},
            {label: 'FECHA DE REGISTRO', key: 'fechaRegistro', format: 'date'},
            {label: 'ESTADO', key: 'estado', format: 'status'}
        ],
        fields: [
            {key: 'nombre', label: 'Nombre', required: true},
            {key: 'apellido', label: 'Apellido', required: true},
            {key: 'correo', label: 'Correo', type: 'email', required: true},
            {key: 'contrasena', label: 'Contraseña (se guardará cifrada)', type: 'password', requiredOnCreate: true},
            {key: 'rol', label: 'Rol', type: 'select', optionsEndpoint: '/api/usuarios/roles', required: true},
            {key: 'estado', label: 'Estado', type: 'select', options: ['Activo', 'Inactivo']}
        ]
    }
};

const moneyFormatter = new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 2
});
const dateFormatter = new Intl.DateTimeFormat('es-CO', {
    dateStyle: 'medium',
    timeStyle: 'short'
});
const dateOnlyFormatter = new Intl.DateTimeFormat('es-CO', {dateStyle: 'medium'});
const toast = document.querySelector('#toast');
const recordDialog = document.querySelector('#recordDialog');
let toastTimeout;
let activeSection;
let activeRecord;
let currentUser;
let csrfToken;

const isAdministrator = () => ['administrador', 'admin']
    .includes(String(currentUser?.rol || '').trim().toLocaleLowerCase('es'));
const isVendor = () => String(currentUser?.rol || '').trim().toLocaleLowerCase('es') === 'vendedor';
const canViewSection = section => isAdministrator()
    || ['inicio', 'productos', 'ventas'].includes(section);
const canCreateInSection = section => isAdministrator() || (isVendor() && section === 'ventas');
const canManageSection = section => isAdministrator() || (isVendor() && section === 'ventas');

const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
})[character]);

function showToast(message) {
    toast.textContent = message;
    toast.classList.add('show');
    clearTimeout(toastTimeout);
    toastTimeout = setTimeout(() => toast.classList.remove('show'), 3200);
}

async function request(url, options = {}) {
    let response;
    try {
        response = await fetch(url, {
            ...options,
            headers: {
                ...(options.body ? {'Content-Type': 'application/json'} : {}),
                ...(options.method && options.method !== 'GET' && csrfToken
                    ? {'X-CSRF-TOKEN': csrfToken}
                    : {}),
                ...options.headers
            }
        });
    } catch (error) {
        throw new Error('No fue posible conectar con la aplicación. Comprueba que Spring Boot siga iniciado.');
    }
    const contentType = response.headers.get('content-type') || '';
    const body = contentType.includes('application/json') ? await response.json() : null;
    if (!response.ok) {
        const error = new Error(body?.message || `La solicitud falló (${response.status}).`);
        error.status = response.status;
        throw error;
    }
    return body;
}

function displayValue(value, column) {
    if (value === null || value === undefined || value === '') return '—';
    if (column.format === 'currency') return moneyFormatter.format(Number(value));
    if (column.format === 'date') {
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? String(value) : dateOnlyFormatter.format(date);
    }
    if (column.format === 'status') {
        const normalized = String(value).toLocaleLowerCase('es');
        const statusClass = ['activo', 'disponible', 'completada'].includes(normalized)
            ? 'complete'
            : ['pendiente'].includes(normalized) ? 'pending' : 'inactive';
        return `<span class="status-badge ${statusClass}">${escapeHtml(value)}</span>`;
    }
    return escapeHtml(value);
}

function renderSection(sectionKey, records) {
    const section = sections[sectionKey];
    const panel = document.querySelector(`#view-${sectionKey}`);
    const headers = section.columns.map(column => `<th>${escapeHtml(column.label)}</th>`).join('');
    const rows = records.map(record => {
        const cells = section.columns.map(column =>
            `<td>${displayValue(record[column.key], column)}</td>`
        ).join('');
        const id = escapeHtml(record[section.idField]);
        const saleDetails = sectionKey === 'ventas'
            ? `<button type="button" data-record-action="details" data-id="${id}">Artículos</button>`
            : '';
        return `<tr data-record-row="${id}">${cells}<td><div class="record-actions">
            ${saleDetails}
            ${canManageSection(sectionKey) ? `<button type="button" data-record-action="edit" data-id="${id}">Editar</button>` : ''}
            ${canManageSection(sectionKey) ? `<button type="button" data-record-action="delete" data-id="${id}">Eliminar</button>` : ''}
        </div></td></tr>`;
    }).join('');
    const emptyRow = `<tr><td colspan="${section.columns.length + 1}">No hay registros.</td></tr>`;

    panel.innerHTML = `
        <div class="module-heading">
            <div><p class="eyebrow">GESTIÓN DE INVENTARIO</p><h1>${escapeHtml(section.title)}</h1><p class="welcome-subtitle">${escapeHtml(section.subtitle)}</p></div>
            ${canCreateInSection(sectionKey) ? `<button class="button button-primary module-action" type="button" data-record-action="create">${escapeHtml(section.action)}</button>` : ''}
        </div>
        <div class="module-summary"><span class="summary-chip"><strong>${records.length}</strong> ${records.length === 1 ? 'registro' : 'registros'}</span></div>
        <section class="surface module-table">
            <div class="table-toolbar">
                <label class="search-box">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>
                    <input type="search" placeholder="Buscar ${escapeHtml(section.title.toLocaleLowerCase('es'))}..." aria-label="Buscar ${escapeHtml(section.title.toLocaleLowerCase('es'))}">
                </label>
            </div>
            <div class="table-wrap">
                <table><thead><tr>${headers}<th>ACCIONES</th></tr></thead><tbody>${rows || emptyRow}</tbody></table>
                <div class="empty-filter">No se encontraron resultados.</div>
            </div>
        </section>
        <p class="prototype-note">Información consultada desde la base de datos.</p>`;

    const searchInput = panel.querySelector('.search-box input');
    searchInput.addEventListener('input', () => {
        const tableRows = [...panel.querySelectorAll('tbody tr')];
        let visibleCount = 0;
        tableRows.forEach(row => {
            const matches = row.textContent.toLocaleLowerCase('es')
                .includes(searchInput.value.trim().toLocaleLowerCase('es'));
            row.hidden = !matches;
            if (matches) visibleCount++;
        });
        panel.querySelector('.empty-filter').style.display = visibleCount ? 'none' : 'block';
    });
}

async function loadSection(sectionKey) {
    const panel = document.querySelector(`#view-${sectionKey}`);
    panel.innerHTML = '<p class="dashboard-message">Cargando registros…</p>';
    try {
        const records = await request(sections[sectionKey].endpoint);
        if (!Array.isArray(records)) throw new Error('El servidor devolvió una respuesta inválida.');
        if (sectionKey === 'productos') {
            document.querySelector('#productsCount').textContent = String(records.length);
        }
        renderSection(sectionKey, records);
    } catch (error) {
        panel.innerHTML = '<p class="dashboard-message">No fue posible cargar los registros.</p>';
        showToast(error.message);
    }
}

function formatLocalDateTime(value) {
    if (!value) return '';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value).slice(0, 16) : dateFormatter.format(date);
}

function renderRecentSales(sales) {
    const rows = sales.slice(0, 5).map(sale => `
        <tr>
            <td><strong class="sale-number">#${escapeHtml(sale.idVenta)}</strong></td>
            <td>${escapeHtml(sale.nombreVendedor || `Usuario ${sale.idUsuario}`)}</td>
            <td>${escapeHtml(formatLocalDateTime(sale.fechaVenta))}</td>
            <td>${escapeHtml(sale.metodoPago)}</td>
            <td class="amount">${escapeHtml(moneyFormatter.format(Number(sale.total || 0)))}</td>
            <td>${displayValue(sale.estado, {format: 'status'})}</td>
        </tr>`).join('');
    document.querySelector('#recentSalesRows').innerHTML = rows;
    document.querySelector('#recentSalesMessage').textContent = sales.length
        ? 'Se muestran las últimas ventas registradas.'
        : 'No hay ventas registradas.';
}

function renderLowStock(products) {
    const lowStock = products.filter(product =>
        product.stock !== null && product.stock !== undefined
        && product.stockMinimo !== null && product.stockMinimo !== undefined
        && Number(product.stock) <= Number(product.stockMinimo)
    );
    document.querySelector('#lowStockValue').textContent = String(lowStock.length);
    document.querySelector('#lowStockProducts').innerHTML = lowStock.length
        ? lowStock.slice(0, 5).map(product => `
            <div class="stock-item">
                <span class="product-thumb thumb-cake">•</span>
                <span class="stock-product"><strong>${escapeHtml(product.nombre)}</strong><small>${escapeHtml(product.categoria || 'Sin categoría')}</small></span>
                <span class="stock-level"><strong>${escapeHtml(product.stock)}</strong><small>disponibles</small></span>
                <span class="stock-indicator low"></span>
            </div>`).join('')
        : '<p class="dashboard-message">No hay productos por debajo del stock mínimo.</p>';
    document.querySelector('#lowStockSummary').innerHTML =
        `<span class="status-dot"></span> ${lowStock.length} productos requieren atención`;
}

async function loadDashboard() {
    try {
        const [sales, products] = await Promise.all([
            request('/api/ventas'),
            request('/api/productos')
        ]);
        if (!Array.isArray(sales) || !Array.isArray(products)) {
            throw new Error('El servidor devolvió una respuesta inválida.');
        }

        const now = new Date();
        const todayKey = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
        const monthKey = todayKey.slice(0, 7);
        const todaySales = sales.filter(sale => String(sale.fechaVenta || '').startsWith(todayKey));
        const monthSales = sales.filter(sale => String(sale.fechaVenta || '').startsWith(monthKey));
        const sum = entries => entries.reduce((total, sale) => total + Number(sale.total || 0), 0);

        document.querySelector('#salesTodayValue').textContent = moneyFormatter.format(sum(todaySales));
        document.querySelector('#salesMonthValue').textContent = moneyFormatter.format(sum(monthSales));
        document.querySelector('#salesMonthLabel').textContent =
            new Intl.DateTimeFormat('es-CO', {month: 'long', year: 'numeric'}).format(now);
        document.querySelector('#activeProductValue').textContent = String(products.filter(product =>
            ['activo', 'disponible'].includes(String(product.estado || '').toLocaleLowerCase('es'))
        ).length);
        document.querySelector('#productsCount').textContent = String(products.length);
        document.querySelector('#recentSalesSummary').textContent = String(todaySales.length);
        renderLowStock(products);
        renderRecentSales(sales);
    } catch (error) {
        document.querySelector('#recentSalesMessage').textContent = 'No fue posible cargar las ventas.';
        document.querySelector('#lowStockProducts').innerHTML =
            '<p class="dashboard-message">No fue posible cargar los productos.</p>';
        showToast(error.message);
    }
}

function optionLabel(option, field) {
    if (field.optionLabel === 'nombreCompleto') {
        return `${option.nombre || ''} ${option.apellido || ''}`.trim();
    }
    return option[field.optionLabel];
}

async function openRecordDialog(sectionKey, record = null) {
    const section = sections[sectionKey];
    activeSection = sectionKey;
    activeRecord = record;
    document.querySelector('#recordDialogTitle').textContent =
        `${record ? 'Editar' : 'Agregar'} ${section.title.toLocaleLowerCase('es')}`;
    document.querySelector('#recordDialogHelp').textContent = sectionKey === 'ventas'
        ? 'Elige al vendedor y agrega los productos. El total se calculará automáticamente.'
        : sectionKey === 'usuarios'
            ? 'La contraseña se guardará cifrada. No necesitas generar un hash.'
            : 'Completa los campos obligatorios marcados con *.';

    try {
        const fields = section.fields.filter(field =>
            (!field.createOnly || !record)
            && !(sectionKey === 'ventas' && field.key === 'idUsuario' && isVendor())
        );
        const renderedFields = await Promise.all(fields.map(async field => {
            const value = record?.[field.key] ?? '';
            const required = field.required || (!record && field.requiredOnCreate);
            let control;
            if (field.type === 'select') {
                let options = field.options || [];
                if (field.optionsEndpoint) {
                    const records = await request(field.optionsEndpoint);
                    options = records.map(option => typeof option === 'string'
                        ? {value: option, label: option}
                        : {value: option[field.optionValue], label: optionLabel(option, field)});
                    if (field.required && !options.length) {
                        throw new Error('Primero registra un usuario para poder crear la venta.');
                    }
                }
                control = `<select name="${escapeHtml(field.key)}" ${required ? 'required' : ''}>
                    ${required ? '<option value="">Selecciona una opción</option>' : '<option value="">Sin seleccionar</option>'}
                    ${options.map(option => {
                        const item = typeof option === 'string' ? {value: option, label: option} : option;
                        return `<option value="${escapeHtml(item.value)}"
                            ${String(item.value) === String(value) ? 'selected' : ''}>
                            ${escapeHtml(item.label)}
                        </option>`;
                    }).join('')}
                </select>`;
            } else {
                control = `<input name="${escapeHtml(field.key)}" type="${escapeHtml(field.type || 'text')}"
                    ${field.step ? `step="${escapeHtml(field.step)}"` : ''}
                    ${field.placeholder ? `placeholder="${escapeHtml(field.placeholder)}"` : ''}
                    ${required ? 'required' : ''}
                    value="${field.type === 'password' ? '' : escapeHtml(value)}">`;
            }
            return `<label class="dialog-field">
                <span>${escapeHtml(field.label)}${required ? ' *' : ''}</span>${control}
            </label>`;
        }));
        const normalFields = renderedFields.filter((_, index) => !fields[index].advanced).join('');
        const advancedFields = renderedFields.filter((_, index) => fields[index].advanced).join('');
        document.querySelector('#recordDialogFields').innerHTML = normalFields
            + (advancedFields ? `<details class="dialog-advanced"><summary>Opciones adicionales</summary><div class="dialog-fields">${advancedFields}</div></details>` : '');

        const saleLines = document.querySelector('#saleLines');
        saleLines.hidden = sectionKey !== 'ventas' || Boolean(record);
        document.querySelector('#saleLineItems').replaceChildren();
        if (sectionKey === 'ventas' && !record) {
            saleProducts = await request(section.lines.endpoint);
            if (!saleProducts.length) {
                throw new Error('Primero registra al menos un producto para crear una venta.');
            }
            addSaleLine();
            updateSaleTotal();
        }
        recordDialog.showModal();
    } catch (error) {
        showToast(error.message);
    }
}

function recordFromForm(form) {
    const values = new FormData(form);
    const record = {};
    for (const field of sections[activeSection].fields) {
        const value = values.get(field.key);
        if (value === null || (field.type === 'password' && !value)) continue;
        if (field.type === 'number') {
            record[field.key] = value === '' ? null : Number(value);
        } else {
            record[field.key] = value === '' ? null : value;
        }
    }
    if (activeSection === 'ventas' && isVendor()) {
        record.idUsuario = currentUser.idUsuario;
    }
    if (activeSection === 'ventas' && !activeRecord) {
        record.detalles = [...document.querySelectorAll('.sale-line')].map(line => ({
            idProducto: Number(line.querySelector('[name="idProducto"]').value),
            cantidad: Number(line.querySelector('[name="cantidad"]').value),
            precioUnitario: Number(line.querySelector('[name="precioUnitario"]').value)
        }));
        if (!record.detalles.length || record.detalles.some(detail =>
            !detail.idProducto || !Number.isFinite(detail.cantidad) || detail.cantidad <= 0
            || !Number.isFinite(detail.precioUnitario) || detail.precioUnitario < 0
        )) {
            throw new Error('Selecciona un producto y una cantidad válida para cada artículo.');
        }
    }
    return record;
}

let saleProducts = [];

function addSaleLine() {
    const line = document.createElement('div');
    line.className = 'sale-line';
    line.innerHTML = `
        <label class="dialog-field sale-line-product"><span>Producto *</span>
            <select name="idProducto" required>
                <option value="">Selecciona un producto</option>
                ${saleProducts.map(product => `<option value="${escapeHtml(product.idProducto)}">${escapeHtml(product.nombre)} — ${escapeHtml(moneyFormatter.format(Number(product.precioVenta || 0)))}</option>`).join('')}
            </select>
        </label>
        <label class="dialog-field"><span>Cantidad *</span>
            <input name="cantidad" type="number" min="0.01" step="any" value="1" required>
        </label>
        <label class="dialog-field"><span>Precio unitario</span>
            <input name="precioUnitario" type="number" min="0" step="any" value="0" required>
        </label>
        <button class="remove-sale-line" type="button">Quitar</button>`;
    document.querySelector('#saleLineItems').append(line);
    line.querySelector('[name="idProducto"]').addEventListener('change', event => {
        const product = saleProducts.find(item => String(item.idProducto) === event.target.value);
        line.querySelector('[name="precioUnitario"]').value = product?.precioVenta ?? 0;
        updateSaleTotal();
    });
    line.addEventListener('input', updateSaleTotal);
    line.querySelector('.remove-sale-line').addEventListener('click', () => {
        if (document.querySelectorAll('.sale-line').length === 1) {
            showToast('Una venta debe tener al menos un producto.');
            return;
        }
        line.remove();
        updateSaleTotal();
    });
}

function updateSaleTotal() {
    const total = [...document.querySelectorAll('.sale-line')].reduce((sum, line) => {
        return sum + Number(line.querySelector('[name="cantidad"]').value || 0)
            * Number(line.querySelector('[name="precioUnitario"]').value || 0);
    }, 0);
    const discount = Number(document.querySelector('[name="descuento"]')?.value || 0);
    document.querySelector('#saleTotal').textContent =
        moneyFormatter.format(Math.max(total - discount, 0));
}

async function saveRecord(form) {
    const section = sections[activeSection];
    let record;
    try {
        record = recordFromForm(form);
    } catch (error) {
        showToast(error.message);
        return;
    }
    const method = activeRecord ? 'PUT' : 'POST';
    const url = activeRecord
        ? `${section.endpoint}/${encodeURIComponent(activeRecord[section.idField])}`
        : section.endpoint;
    try {
        await request(url, {method, body: JSON.stringify(record)});
        recordDialog.close();
        showToast('Registro guardado correctamente.');
        await loadSection(activeSection);
        await loadDashboard();
    } catch (error) {
        showToast(error.message);
    }
}

async function deleteRecord(sectionKey, id) {
    if (!window.confirm('¿Deseas eliminar este registro?')) return;
    try {
        await request(`${sections[sectionKey].endpoint}/${encodeURIComponent(id)}`, {method: 'DELETE'});
        showToast('Registro eliminado correctamente.');
        await loadSection(sectionKey);
        await loadDashboard();
    } catch (error) {
        showToast(error.message);
    }
}

async function toggleSaleDetails(button, saleId) {
    const row = button.closest('tr');
    const existing = row.nextElementSibling;
    if (existing?.classList.contains('sale-detail-row')) {
        existing.remove();
        return;
    }
    button.disabled = true;
    try {
        const [details, products] = await Promise.all([
            request(`/api/detalles-venta?ventaId=${encodeURIComponent(saleId)}`),
            request('/api/productos')
        ]);
        if (!Array.isArray(details) || !Array.isArray(products)) {
            throw new Error('No fue posible leer los artículos de esta venta.');
        }
        const productNames = new Map(products.map(product => [product.idProducto, product.nombre]));
        const detailRows = details.length
            ? details.map(detail => `<tr>
                <td>${escapeHtml(productNames.get(detail.idProducto) || `Producto ${detail.idProducto}`)}</td>
                <td>${escapeHtml(detail.cantidad)}</td>
                <td>${escapeHtml(moneyFormatter.format(Number(detail.precioUnitario || 0)))}</td>
                <td>${escapeHtml(moneyFormatter.format(Number(detail.subtotal || 0)))}</td>
            </tr>`).join('')
            : '<tr><td colspan="4">Esta venta no tiene artículos registrados.</td></tr>';
        row.insertAdjacentHTML('afterend', `<tr class="sale-detail-row"><td colspan="${sections.ventas.columns.length + 1}">
            <div class="sale-detail-content"><h3>Artículos de la venta #${escapeHtml(saleId)}</h3>
                <table><thead><tr><th>PRODUCTO</th><th>CANTIDAD</th><th>PRECIO</th><th>SUBTOTAL</th></tr></thead>
                <tbody>${detailRows}</tbody></table>
            </div>
        </td></tr>`);
        button.textContent = 'Ocultar';
    } catch (error) {
        showToast(error.message);
    } finally {
        button.disabled = false;
    }
}

function pathForView(view) {
    return view === 'inicio' ? '/' : sections[view].route;
}

function initialViewFromLocation() {
    const hashView = window.location.hash.slice(1);
    if (sections[hashView]) return hashView;
    const pathView = Object.keys(sections).find(key => sections[key].route === window.location.pathname);
    if (pathView) return pathView;
    const templateView = document.body.dataset.initialView;
    if (sections[templateView]) return templateView;
    return 'inicio';
}

function showView(viewName, updateHistory = true) {
    const requestedView = sections[viewName] ? viewName : 'inicio';
    const view = canViewSection(requestedView) ? requestedView : 'inicio';
    if (view !== requestedView) showToast('No tienes permiso para consultar esta sección.');
    document.querySelectorAll('.view-panel').forEach(panel =>
        panel.classList.toggle('visible', panel.id === `view-${view}`)
    );
    document.querySelectorAll('.nav-link').forEach(button =>
        button.classList.toggle('active', button.dataset.view === view)
    );
    document.querySelector('#breadcrumbCurrent').textContent =
        view === 'inicio' ? 'Inicio' : sections[view].title;
    document.querySelector('#sidebar').classList.remove('open');
    if (updateHistory) history.pushState(null, '', pathForView(view));
    if (view === 'inicio') {
        void loadDashboard();
    } else {
        void loadSection(view);
    }
}

document.querySelectorAll('[data-view], [data-go]').forEach(button => {
    button.addEventListener('click', event => {
        event.preventDefault();
        showView(button.dataset.view || button.dataset.go);
    });
});

document.addEventListener('click', event => {
    const button = event.target.closest('[data-record-action]');
    if (!button) return;
    const sectionKey = button.closest('[data-section]')?.dataset.section;
    if (!sectionKey) return;
    if (button.dataset.recordAction === 'create') {
        void openRecordDialog(sectionKey);
        return;
    }
    const id = button.dataset.id;
    if (button.dataset.recordAction === 'delete') {
        void deleteRecord(sectionKey, id);
    } else if (button.dataset.recordAction === 'edit') {
        request(sections[sectionKey].endpoint + '/' + encodeURIComponent(id))
            .then(record => openRecordDialog(sectionKey, record))
            .catch(error => showToast(error.message));
    } else if (button.dataset.recordAction === 'details') {
        void toggleSaleDetails(button, id);
    }
});

document.querySelector('#recordForm').addEventListener('submit', event => {
    event.preventDefault();
    if (!event.currentTarget.reportValidity()) return;
    void saveRecord(event.currentTarget);
});

document.querySelector('#closeRecordDialog').addEventListener('click', () => recordDialog.close());
document.querySelector('#cancelRecordDialog').addEventListener('click', () => recordDialog.close());
document.querySelector('#addSaleLine').addEventListener('click', addSaleLine);
document.querySelector('#recordDialogFields').addEventListener('input', event => {
    if (event.target.name === 'descuento') updateSaleTotal();
});
document.querySelector('#mobileMenu').addEventListener('click', () => {
    document.querySelector('#sidebar').classList.toggle('open');
});
document.querySelector('.notification-button').addEventListener('click', () => {
    showToast('No hay notificaciones nuevas.');
});
document.querySelector('#logoutButton').addEventListener('click', async () => {
    try {
        await request('/api/auth/logout', {method: 'POST'});
        window.location.assign('/login');
    } catch (error) {
        showToast(error.message);
    }
});
window.addEventListener('popstate', () => showView(initialViewFromLocation(), false));

const todayLabel = document.querySelector('#todayLabel');
todayLabel.textContent = new Intl.DateTimeFormat('es-CO', {
    weekday: 'short', day: 'numeric', month: 'short', year: 'numeric'
}).format(new Date());
document.querySelector('#welcomeDate').textContent = new Intl.DateTimeFormat('es-CO', {
    weekday: 'long', day: 'numeric', month: 'long'
}).format(new Date()).toLocaleUpperCase('es-CO');

async function initializeApplication() {
    try {
        currentUser = await request('/api/auth/me');
        csrfToken = currentUser.csrfToken;
        document.querySelector('#profileName').textContent = currentUser.nombreCompleto;
        document.querySelector('#profileRole').textContent = currentUser.rol;
        document.querySelector('#profileAvatar').textContent = currentUser.nombre
            .split(/\s+/).slice(0, 2).map(part => part[0]).join('').toLocaleUpperCase('es');
        document.querySelectorAll('[data-admin-only]').forEach(element => {
            element.hidden = !isAdministrator();
        });
        showView(initialViewFromLocation(), false);
    } catch (error) {
        if (error.status === 401 || error.status === 403) {
            window.location.replace('/login');
            return;
        }
        showToast(error.message);
    }
}

void initializeApplication();
