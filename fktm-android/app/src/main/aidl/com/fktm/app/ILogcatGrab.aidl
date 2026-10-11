// AIDL for the Shizuku UserService that captures doorlock credentials.
//
// The implementation runs in a separate process started by Shizuku with the
// identity of shell (uid 2000) or root, which is what lets it read logcat of
// other apps.
package com.fktm.app;

interface ILogcatGrab {
    /**
     * Run `logcat` with the Tmall Campus filters and block until an
     * activationKey line shows up, or until [timeoutMs] elapses.
     *
     * **The hit is written to [resultPath] the instant it is found**, so the
     * credential survives even if the caller's process is frozen/killed right
     * afterwards (ColorOS freezes background apps aggressively). The caller
     * therefore treats the file, not this return value, as the source of truth.
     *
     * @param timeoutMs  how long to keep listening
     * @param resultPath absolute path of a JSON file to write the hit into
     * @return the captured raw log text (empty string means nothing matched)
     */
    String grab(int timeoutMs, String resultPath);

    /**
     * Live progress / diagnostics of [grab] — updated at every step, so a
     * caller that gives up can tell exactly which step was reached.
     */
    String diag();
}
