// dashboard.js - Dashboard logic and token management

let countdownInterval = null;

document.addEventListener('DOMContentLoaded', () => {
    fetchUserInfo();
    setupEventListeners();
});

// Fetch user info from backend (T025, T024)
async function fetchUserInfo() {
    try {
        const response = await fetch('https://backend.brunorozendo.dev/api/auth/user', {
            method: 'GET',
            credentials: 'include'  // Include session cookie
        });

        if (response.status === 401) {
            // Session expired - redirect to login (T030)
            console.debug('Session expired, redirecting to login');
            window.location.href = '/?error=session_expired';
            return;
        }

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const userData = await response.json();
        displayUserProfile(userData);  // T026
        startCountdown(userData.expiresAt);  // T027
    } catch (error) {
        console.error('Error fetching user info:', error);
        document.getElementById('user-name').textContent = 'Error loading profile';
        document.getElementById('user-email').textContent = 'Please refresh the page';
    }
}

// Display user profile (T026)
function displayUserProfile(userData) {
    const userName = document.getElementById('user-name');
    const userEmail = document.getElementById('user-email');
    const userAvatar = document.getElementById('user-avatar');

    // Use defaults for missing fields (FR-014)
    const name = userData.name || 'Unknown User';
    const email = userData.email || 'unknown@example.com';
    const picture = userData.picture || '/assets/default-avatar.svg';

    userName.textContent = name;
    userEmail.textContent = email;
    userAvatar.src = picture;

    // Log debug info if fields are missing (T028)
    if (!userData.name || !userData.email || !userData.picture) {
        console.debug('Profile has missing fields:', {
            name: !userData.name,
            email: !userData.email,
            picture: !userData.picture
        });
    }

    console.debug('User profile displayed:', { name, email });
}

// Start countdown timer (T027)
function startCountdown(expiresAt) {
    const countdownElement = document.getElementById('countdown');

    // Clear any existing countdown
    if (countdownInterval) {
        clearInterval(countdownInterval);
    }

    function updateCountdown() {
        const now = Date.now();
        const remaining = expiresAt - now;

        if (remaining <= 0) {
            countdownElement.textContent = 'Expired (refreshing...)';
            clearInterval(countdownInterval);
            // Auto-refresh token when countdown reaches 0 (T035)
            refreshToken();
            return;
        }

        const seconds = Math.floor(remaining / 1000);
        const minutes = Math.floor(seconds / 60);
        const displaySeconds = seconds % 60;

        countdownElement.textContent = `${minutes}:${displaySeconds.toString().padStart(2, '0')}`;
    }

    updateCountdown();
    countdownInterval = setInterval(updateCountdown, 1000);
}

// Refresh token (T033)
async function refreshToken() {
    const refreshButton = document.getElementById('refresh-button');

    try {
        // Show visual feedback (T036)
        refreshButton.disabled = true;
        refreshButton.textContent = 'Refreshing...';

        const response = await fetch('https://backend.brunorozendo.dev/api/auth/refresh', {
            method: 'POST',
            credentials: 'include'
        });

        if (response.status === 401) {
            console.debug('Session expired during refresh, redirecting to login');
            window.location.href = '/?error=session_expired';
            return;
        }

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();
        console.debug('Token refreshed successfully, new expiration:', new Date(data.expiresAt));

        // Update countdown with new expiration (T033, T035)
        startCountdown(data.expiresAt);
    } catch (error) {
        console.error('Token refresh failed:', error);
        // Redirect to login on failure (T033)
        window.location.href = '/?error=token_refresh_failed';
    } finally {
        // Restore button state (T036)
        refreshButton.disabled = false;
        refreshButton.textContent = 'Refresh Now';
    }
}

// Logout (T038)
async function logout() {
    try {
        await fetch('https://backend.brunorozendo.dev/api/auth/logout', {
            method: 'POST',
            credentials: 'include'
        });
    } catch (error) {
        console.error('Logout request failed:', error);
        // Continue with redirect even if request fails
    }

    // Redirect to login (T038)
    window.location.href = '/';
}

// Setup event listeners
function setupEventListeners() {
    const refreshButton = document.getElementById('refresh-button');
    const logoutButton = document.getElementById('logout-button');

    if (refreshButton) {
        refreshButton.addEventListener('click', refreshToken);  // T034
    }

    if (logoutButton) {
        logoutButton.addEventListener('click', logout);  // T039
    }
}
