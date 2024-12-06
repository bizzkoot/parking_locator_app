data class ParkingLocation(
    val latitude: Double,
    val longitude: Double,
    val floorLevel: String,
    val photoPath: String,
    val timestamp: Long = System.currentTimeMillis()
)