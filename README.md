Android Slider Preference Library
=================================

![Screenshot](https://raw.github.com/jayschwa/AndroidSliderPreference/master/screenshot.png)

## Overview

* Slider represents a `float` between `0.0` and `1.0`
  * Access with `SliderPreference.getValue()` or [`SharedPreferences.getFloat()`][shar]
* Supports multiple summaries (e.g. "Low", "Medium", "High") and selects one based on the slider's position
  * Java: `SliderPreference.setSummary(CharSequence[] summaries)`
  * XML: `android:summary="@array/string_array_of_summaries"`
  * A single `String` still works too
* Subclass of [`DialogPreference`][diag]
  * Supports all [dialog-specific attributes][datr] such as `android:dialogMessage`
  * Visually-consistent with Android's built-in preferences
  * Less error-prone than displaying the slider directly on the settings screen
* [MIT License](#license)

[datr]: https://developer.android.com/reference/android/preference/DialogPreference.html#lattrs "DialogPreference attributes"
[diag]: https://developer.android.com/reference/android/preference/DialogPreference.html "DialogPreference"
[shar]: https://developer.android.com/reference/android/content/SharedPreferences.html#getFloat(java.lang.String,%20float) "getFloat()"

## How To Use

### Add the library to your application

Before you can add a `SliderPreference` to your application, you must first add a library reference:

1. Clone or download a copy of the library
2. Import the library into Eclipse: File menu -> Import -> Existing Project into Workspace
3. Open your application's project properties and [add a library reference][ref] to "SliderPreference"

[ref]: https://developer.android.com/tools/projects/projects-eclipse.html#ReferencingLibraryProject

### Add a slider to your application

``` XML
<!-- preferences.xml -->
<net.jayschwa.android.preference.SliderPreference
    android:key="my_slider"
    android:title="@string/slider_title"
    android:summary="@array/slider_summaries"
    android:defaultValue="@string/slider_default"
    android:dialogMessage="@string/slider_message" />
```
``` XML
<!-- strings.xml -->
<string name="slider_title">Temperature</string>
<string-array name="slider_summaries">
    <!-- You can define as many summaries as you'd like -->
    <!-- The active summary will reflect the preference's current value -->
    <item>Freezing</item> <!-- 0.00 to 0.25 -->
    <item>Chilly</item>   <!-- 0.25 to 0.50 -->
    <item>Warm</item>     <!-- 0.50 to 0.75 -->
    <item>Boiling</item>  <!-- 0.75 to 1.00 -->
</string-array>
<item name="slider_default" format="float" type="string">0.5</item>
<string name="slider_message">Optional message displayed in the dialog above the slider</string>
```

It is possible to define the default value directly in the attribute. The summary can also be a regular string, instead of a string array:

``` XML
<net.jayschwa.android.preference.SliderPreference
    android:summary="This summary is static and boring"
    android:defaultValue="0.5" />
```

## Background

Sliders are recommended by Android's official [design documentation][ptrn] for specific types of settings:

> Use this pattern for a setting where the range of values are not discrete and fall along a continuum.
>
> ![Slider design pattern example](https://developer.android.com/design/media/settings_slider.png)

Despite this recommendation, the Android SDK does not actually provide a [`Preference`][pref] with slider functionality. Various custom implementations can be found around the web, but many have issues such as:

* The slider is displayed directly on the settings screen
  * Higher chance of accidental clicks
  * No way to confirm or cancel potential changes
* Discrete values are displayed to the user
  * Not ideal for this design pattern

This library aims to be as consistent as possible with the design pattern and Android's built-in [`Preference`][pref] implementations.

[ptrn]: https://developer.android.com/design/patterns/settings.html#patterns "Settings Design Patterns"
[pref]: https://developer.android.com/reference/android/preference/Preference.html "Preference"

## License

This library is licensed under the [MIT License][mit]. A copy of the license is provided in [LICENSE.txt][copy]:

> Copyright 2012 Jay Weisskopf
>
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

[copy]: https://raw.github.com/jayschwa/AndroidSliderPreference/master/LICENSE.txt
[mit]: http://opensource.org/licenses/MIT "Open Source Initiative - The MIT License"