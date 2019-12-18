# AndroidSurfaceExample
Example for post https://habr.com/ru/post/480878/ about of interaction android camera api v2, glsurafceview, mediacodec and surfaces.

This sample of outputting  camera preview on screen with overlay custom drawable and capability record result video to file.
For composition video. 


<img src="https://github.com/tttzof351/AndroidSurfaceExample/blob/master/img/Screenshot.png" width="40%">


For composition video used OpenGL + external texture, for record used MediaCodec + EGLSurface
Common scheme:

<img src="https://github.com/tttzof351/AndroidSurfaceExample/blob/master/img/two_surfaces.png" width="60%">

Because camera api, glsurfaceview, mediacodec etc have async api we wrap work with them in state machines:

<img src="https://github.com/tttzof351/AndroidSurfaceExample/blob/master/img/full_diagram.png" width="50%">

