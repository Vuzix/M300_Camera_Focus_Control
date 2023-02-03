# M300_Camera_Focus_Control

This zip file contains an Android Studio projects which to show how to implement the auto
focus feature in Camera API 1

AutoFocus
-----------------------
This sample app shows how to operate the auto focus in Camera API 1 on the M300.

The best way to use the auto focus in M300 is to leave the focus alone. The M300's
camera platform will default to continuous automatic focus. To keep the image in focus
you do not need to make any setting changes or focus calls.

If the developer uses the Camera API 1 to call the autofocus(), it will potentially
cause the auto focus to stop working properly.

This sample app shows how to use the autofocus() correctly with cancelAutoFocus()to create
a push-to-focus implementation.  Press the enter key or double-tap the touchpad to focus.
