# Keep all BorgDrone implementations and their members — prevents R8 class
# merging from creating circular dependencies in the drone resolution graph.
-keep class * implements net.kibotu.borg.BorgDrone { *; }
