# Payload entry and runtime names are loaded by string from the host loader.
-keep class dev.mindlink.compose.SidePanelUi { public *; }
-keep class dev.mindlink.compose.SettingsUi { public *; }
-keep class androidx.compose.runtime.Composer { *; }
-keep class kotlin.Unit { *; }

-keepattributes Signature,InnerClasses,EnclosingMethod
