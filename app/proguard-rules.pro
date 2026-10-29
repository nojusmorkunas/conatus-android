# Glance creates the widget's action callbacks from their class names, so R8 must keep their
# no-argument constructors.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
