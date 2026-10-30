package com.raju.aiassistant.ui.home.visuals

import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal 3D math for the holographic core: points on a unit scale, rotated in 3D and projected
 * with a pinhole camera. x → right, y → down, z → towards the viewer. Pure, unit-tested.
 */
data class Vec3(val x: Float, val y: Float, val z: Float)

/** A projected point: screen position, perspective [scale] (bigger = nearer) and [depth] 0 (back) … 1 (front). */
data class Projected(val x: Float, val y: Float, val scale: Float, val depth: Float)

fun Vec3.rotateX(a: Float): Vec3 {
    val c = cos(a); val s = sin(a)
    return Vec3(x, y * c - z * s, y * s + z * c)
}

fun Vec3.rotateY(a: Float): Vec3 {
    val c = cos(a); val s = sin(a)
    return Vec3(x * c + z * s, y, -x * s + z * c)
}

fun Vec3.rotateZ(a: Float): Vec3 {
    val c = cos(a); val s = sin(a)
    return Vec3(x * c - y * s, x * s + y * c, z)
}

/**
 * Perspective projection around ([cx], [cy]) with [radius] pixels per unit. [camera] is the
 * camera's distance in units; objects up to ±1.9 units deep stay in front of it.
 */
fun project(p: Vec3, cx: Float, cy: Float, radius: Float, camera: Float = 4f): Projected {
    val scale = camera / (camera - p.z)
    return Projected(cx + p.x * radius * scale, cy + p.y * radius * scale, scale, ((p.z + 1.9f) / 3.8f).coerceIn(0f, 1f))
}

/** Points of a circle of [r] units in the x/y plane (a ring before it is tilted). */
fun ringPoints(r: Float, count: Int): List<Vec3> = List(count) { i ->
    val a = (2.0 * Math.PI * i / count).toFloat()
    Vec3(r * cos(a), r * sin(a), 0f)
}

/** Latitude rings and longitude meridians of a unit sphere, as closed point loops (the wireframe core). */
fun sphereWireframe(latitudes: Int, meridians: Int, segments: Int): List<List<Vec3>> {
    val loops = mutableListOf<List<Vec3>>()
    for (i in 1..latitudes) {
        val phi = (Math.PI * i / (latitudes + 1)).toFloat()
        val y = cos(phi); val r = sin(phi)
        loops += List(segments) { k ->
            val a = (2.0 * Math.PI * k / segments).toFloat()
            Vec3(r * cos(a), y, r * sin(a))
        }
    }
    for (m in 0 until meridians) {
        val theta = (Math.PI * m / meridians).toFloat()
        loops += List(segments) { k ->
            val a = (2.0 * Math.PI * k / segments).toFloat()
            Vec3(sin(a) * cos(theta), cos(a), sin(a) * sin(theta))
        }
    }
    return loops
}
