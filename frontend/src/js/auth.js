// auth.js - Handle OAuth2 login flow

document.addEventListener('DOMContentLoaded', () => {
    const loginButton = document.getElementById('login-button');
    const errorMessage = document.getElementById('error-message');

    // Handle login button click
    if (loginButton) {
        loginButton.addEventListener('click', () => {
            console.debug('Login button clicked, redirecting to OAuth2 authorization');
            // Redirect to Spring Security's auto-generated OAuth2 login endpoint
            window.location.href = 'https://backend.brunorozendo.dev/oauth2/authorization/google';
        });
    }

    // Check for error parameters in URL (from T022)
    const urlParams = new URLSearchParams(window.location.search);
    const error = urlParams.get('error');

    if (error && errorMessage) {
        let errorText = 'Authentication failed. ';

        switch (error) {
            case 'session_expired':
                errorText = 'Your session has expired. Please sign in again.';
                break;
            case 'access_denied':
                errorText = 'Access was denied. You must grant permissions to continue.';
                break;
            case 'token_exchange_failed':
                errorText = 'Failed to exchange authorization code. Please try again.';
                break;
            case 'google_unavailable':
                errorText = 'Google services are temporarily unavailable. Please try again later.';
                break;
            default:
                errorText += 'Please try again.';
        }

        errorMessage.textContent = errorText;
        errorMessage.style.display = 'block';

        // Add "Try Again" functionality
        const tryAgainLink = document.createElement('a');
        tryAgainLink.href = '#';
        tryAgainLink.textContent = ' Try Again';
        tryAgainLink.addEventListener('click', (e) => {
            e.preventDefault();
            window.location.href = window.location.pathname; // Clear error params
        });
        errorMessage.appendChild(tryAgainLink);

        console.debug('Authentication error displayed:', error);
    }
});
