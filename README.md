<p align="center">
  <img src="assets/readme-header.png" alt="NINE’tok" width="160"/>
</p>

<p align="center">
  <a href="LICENSE"><img alt="license" src="https://img.shields.io/badge/license-GPLv3-blue.svg" /></a>
  <a href="https://github.com/MorpheApp/morphe-cli"><img alt="patcher" src="https://img.shields.io/badge/for-Morphe-00b894.svg" /></a>
  <a href="https://www.apkmirror.com/apk/tiktok-pte-ltd/tik-tok-including-musical-ly/tiktok-47-4-3-release/"><img alt="TikTok 47.4.3" src="https://img.shields.io/badge/TikTok-47.4.3-ff0050.svg" /></a>
</p>

# NINE’tok

**NINE’tok (9’tok) is a set of TikTok patches for use with Morphe.** It targets global TikTok 47.4.3, package `com.zhiliaoapp.musically`.

What it changes compared with the project it comes from:

- its own name and launcher icon (`9’tok` under the icon, `NINE’tok` in the settings menu);
- region spoof is on out of the box (SIM, locale and time zone, region fields in requests), so saved posts, reposts and shared videos stay visible where TikTok would hide foreign content;
- the startup splash ad, comment pop-up ads and the floating coin and rewards promotions are hidden by default;
- Duet and Stitch show on every video, and sign-in skips passkeys, by default.

Every switch can still be changed in **Settings and privacy → NINE’tok**.

## Credits

NINE’tok is a modified version of [**tiktok-patches-for-morphe**](https://github.com/icysymmetra/tiktok-patches-for-morphe) by **icysymmetra**, which is built on [Hushfeed](https://github.com/SysAdminDoc/hushfeed), [Morphe](https://github.com/MorpheApp/morphe-patches) and [ReVanced](https://gitlab.com/ReVanced/revanced-patches). Almost all of the patch code is theirs. If you like the patches, consider [supporting the original author](https://ko-fi.com/P5P5YOUU7).

## Using it

1. Get the official TikTok 47.4.3 APK (not a bundle) for `com.zhiliaoapp.musically`.
2. Add this repository as a patch source in Morphe Manager 2.0.0 or newer, or download the `.mpp` from Releases and use it with the Morphe CLI.
3. Optional: tick **Change app name** and **Custom launcher icon**; for the icon, pick `assets/branding/ninetok-icon.png` with the *Black background* style.

This repository publishes patches only. Do not upload patched TikTok APKs here: they contain TikTok’s own code.

## License

GPLv3 with the additional section 7 terms in [NOTICE](NOTICE). NOTICE must stay with every copy, source or built, and stays reachable in the app under Settings → About → Licenses.
