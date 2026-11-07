const form = document.querySelector('#loginForm');
const errorMessage = document.querySelector('#loginError');

form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!form.reportValidity()) return;

    const submitButton = form.querySelector('button[type="submit"]');
    submitButton.disabled = true;
    errorMessage.hidden = true;
    try {
        const values = new FormData(form);
        const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                correo: values.get('correo'),
                contrasena: values.get('contrasena')
            })
        });
        const body = await response.json();
        if (!response.ok) {
            throw new Error(body.message || 'No fue posible iniciar sesión.');
        }
        window.location.assign('/');
    } catch (error) {
        errorMessage.textContent = error.message || 'No fue posible conectar con la aplicación.';
        errorMessage.hidden = false;
    } finally {
        submitButton.disabled = false;
    }
});
