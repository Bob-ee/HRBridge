package com.example.runh10.media

import android.service.notification.NotificationListenerService

/**
 * Empty notification listener: exists solely so MediaSessionManager grants us
 * getActiveSessions() for the watch-local music fallback. The user enables it
 * under Settings → Apps → Special access → Notification access.
 */
class WatchMediaListenerService : NotificationListenerService()
