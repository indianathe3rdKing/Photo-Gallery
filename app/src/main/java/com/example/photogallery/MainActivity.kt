package com.example.photogallery

import android.os.Bundle
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.annotation.Size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import com.example.photogallery.ui.theme.PhotoGalleryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoGalleryTheme {
                // Use the top-level App() which manages activeId and shows FullScreenPhoto
                Scaffold(modifier = Modifier.fillMaxSize()) { contentPadding ->
                    App(photos = PhotoRepo.photos, modifier = Modifier.padding(contentPadding).fillMaxSize())
                }
            }
        }
    }
}

class Photo(val id: Int, val url: Int, val title: String)

object PhotoRepo {
    val photos = listOf(
        Photo(1, R.drawable.one, "One"),
        Photo(2, R.drawable.two, "Two"),
        Photo(3, R.drawable.three, "Three"),
        Photo(4, R.drawable.four, "Two"),
        Photo(5, R.drawable.five, "One"),
        Photo(6, R.drawable.six, "Two"),
        Photo(7, R.drawable.seven, "One"),
        Photo(8, R.drawable.eight, "Two"),
        Photo(9, R.drawable.nine, "Two"),

        )
}

@Composable
fun App(photos: List<Photo>, modifier: Modifier = Modifier) {
    var activeId by rememberSaveable { mutableStateOf<Int?>(null) }

    // Pass a callback to the grid so tapping a photo updates activeId here.
    PhotoGrid(
        photos = photos,
        modifier = modifier.fillMaxSize(),
        onPhotoClick = { photo -> activeId = photo.id }
    )

    if (activeId != null) {
        FullScreenPhoto(
            photo = photos.first { it.id == activeId },
            onDismiss = { activeId = null }
        )
    }
}

@Composable
private fun FullScreenPhoto(
    photo: Photo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Scrim(onDismiss, Modifier.fillMaxSize())
        PhotoCard(photo)
    }

}

@Composable
private fun Scrim(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strClose = stringResource(id = R.string.close)
    val focusRequester = remember { FocusRequester() }

    // BackHandler handles Android back button while Scrim is visible
    BackHandler {
        onDismiss()
    }

    Box(
        modifier
            .fillMaxSize()
            // request focus so onKeyEvent can receive key presses (Escape on desktop)
            .focusRequester(focusRequester)
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
            .semantics {
                onClick(label = strClose) { onDismiss(); true }
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .background(Color.DarkGray.copy(0.75f))
    )

    LaunchedEffect(Unit) {
        // Ensure the scrim has focus to receive key events (desktop/Escape)
        focusRequester.requestFocus()
    }
}

@Composable
private fun PhotoGrid(
    photos: List<Photo>,
    modifier: Modifier = Modifier,
    onPhotoClick: (Photo) -> Unit = {}
) {
    LazyVerticalGrid(columns = GridCells.Adaptive(128.dp), modifier = modifier) {
        items(photos, key = { it.id }) { photo ->
            PhotoCard(photo = photo, onClick = { onPhotoClick(photo) })
        }
    }
}


@Composable
fun PhotoCard(photo: Photo, onClick: (() -> Unit)? = null) {
    // We'll cap decoded bitmap size to avoid drawing extremely large bitmaps that crash the Canvas.
    // Choose a reasonable max dimension (in dp) for grid items and convert to pixels.
    val density = LocalDensity.current
    val maxDp = 200.dp // cap the longer side to ~200dp
    val maxPx = with(density) { maxDp.toPx().toInt() }

    // Try to load a scaled ImageBitmap from resources.
    val scaled: ImageBitmap? = rememberScaledImageBitmap(photo.url, maxPx)

    var offset by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableStateOf(1f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
            .pointerInput(onClick) {
                detectTapGestures(
                    onDoubleTap = { tabOffset ->
                        zoom = if (zoom > 1f) zoom else 2f
                        offset = calculateDoubleTapOffset(zoom, size, tabOffset)
                    }
                )
            }
            .semantics { if (onClick != null) onClick(label = photo.title) { onClick(); true } }
            .pointerInput(Unit) {
                detectTransformGestures(
                    onGesture = { centroid, pan, gestureZoom, _ ->
                        offset = offset.calculateNewOffset(
                            pan, centroid, zoom, size, gestureZoom
                        )
                        zoom = maxOf(1f, zoom * gestureZoom)
                    })
            }
            .pointerInput(onClick){
                detectTapGestures(
                    onTap = {
                        onClick?.invoke()
                    }
                )
            }
            .graphicsLayer(
                translationX = -offset.x * zoom,
                translationY = -offset.y * zoom,
                scaleX = zoom,
                scaleY = zoom,
                transformOrigin = TransformOrigin(0f,0f)

            )
            .aspectRatio(1f)
    ) {
        if (scaled != null) {
            Image(
                bitmap = scaled,
                contentDescription = photo.title,
                contentScale= ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback: small placeholder text if loading fails
            Text(text = photo.title)
        }
    }
}

fun calculateDoubleTapOffset(
    zoom: Float,
    size: IntSize,
    tapOffset: Offset
): Offset {
    val newOffset = Offset(tapOffset.x, tapOffset.y)
    return Offset(
        newOffset.x.coerceIn(0f, (size.width / zoom) * (zoom - 1f)),
        newOffset.y.coerceIn(0f, (size.height / zoom) * (zoom - 1f))
    )
}

fun Offset.calculateNewOffset(
    pan: Offset,
    centroid: Offset,
    zoom: Float,
    size: IntSize,
    gestureZoom: Float,

    ): Offset {
    val newScale = maxOf(1f, zoom * gestureZoom)
    val newOffset = (pan + centroid / zoom) - (centroid / newScale + pan / zoom)
    return Offset(
        newOffset.x.coerceIn(0f, (size.width / zoom) * (zoom - 1f)),
        newOffset.y.coerceIn(0f, (size.height / zoom) * (zoom - 1f))
    )
}

@Composable
private fun rememberScaledImageBitmap(@DrawableRes resId: Int, maxPx: Int): ImageBitmap? {
    val context = LocalContext.current
    return remember(resId, maxPx) {
        // First decode bounds only to get dimensions
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resId, bounds)
        val origW = bounds.outWidth
        val origH = bounds.outHeight
        if (origW <= 0 || origH <= 0) return@remember null

        // Compute inSampleSize (power of two) so that the longer side <= maxPx
        var inSampleSize = 1
        val longer = maxOf(origW, origH)
        if (longer > maxPx) {
            val halfLonger = longer / 2
            while (halfLonger / inSampleSize > maxPx) {
                inSampleSize *= 2
            }
        }

        val options = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }

        val bmp = BitmapFactory.decodeResource(context.resources, resId, options)
        bmp?.asImageBitmap()
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    PhotoGalleryTheme {
        PhotoCard(PhotoRepo.photos.first())
    }
}
