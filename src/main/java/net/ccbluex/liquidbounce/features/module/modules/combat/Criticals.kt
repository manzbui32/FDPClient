/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.movement.Flight
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPackets
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import java.util.*
import kotlin.math.abs

object Criticals : Module("Criticals", Category.COMBAT) {

    val mode by choices(
        "Mode",
        arrayOf(
            "Packet",
            "NCPPacket",
            "BlocksMC",
            "BlocksMC2",
            "NoGround",
            "Hop",
            "TPHop",
            "Jump",
            "LowJump",
            "CustomMotion",
            "Visual",
            "Intave",
            "IntaveSilent",
            "IntaveAdvanced" // Thêm chế độ advanced mới
        ),
        "Packet"
    )

    val delay by int("Delay", 0, 0..500)
    private val hurtTime by int("HurtTime", 10, 0..10)
    private val customMotionY by float("Custom-Y", 0.2f, 0.01f..0.42f) { mode == "CustomMotion" }
    private val intaveRandomness by float("Intave-Random", 0.0001f, 0.00001f..0.001f) { mode.contains("Intave", true) }
    private val intavePacketCount by int("Intave-Packets", 3, 2..5) { mode.contains("Intave", true) }

    val msTimer = MSTimer()
    private val random = Random()

    override fun onEnable() {
        if (mode == "NoGround")
            mc.thePlayer.tryJump()
    }

    val onAttack = handler<AttackEvent> { event ->
        if (event.targetEntity is EntityLivingBase) {
            val thePlayer = mc.thePlayer ?: return@handler
            val entity = event.targetEntity

            if (!thePlayer.onGround || thePlayer.isOnLadder || thePlayer.isInWeb || thePlayer.isInLiquid ||
                thePlayer.ridingEntity != null || entity.hurtTime > hurtTime ||
                Flight.handleEvents() || !msTimer.hasTimePassed(delay)
            )
                return@handler

            val (x, y, z) = thePlayer

            when (mode.lowercase()) {
                "packet" -> {
                    sendPackets(
                        C04PacketPlayerPosition(x, y + 0.0625, z, true),
                        C04PacketPlayerPosition(x, y, z, false)
                    )
                    thePlayer.onCriticalHit(entity)
                }

                "ncppacket" -> {
                    sendPackets(
                        C04PacketPlayerPosition(x, y + 0.11, z, false),
                        C04PacketPlayerPosition(x, y + 0.1100013579, z, false),
                        C04PacketPlayerPosition(x, y + 0.0000013579, z, false)
                    )
                    mc.thePlayer.onCriticalHit(entity)
                }

                "blocksmc" -> {
                    sendPackets(
                        C04PacketPlayerPosition(x, y + 0.001091981, z, true),
                        C04PacketPlayerPosition(x, y, z, false)
                    )
                }

                "blocksmc2" -> {
                    if (thePlayer.ticksExisted % 4 == 0) {
                        sendPackets(
                            C04PacketPlayerPosition(x, y + 0.0011, z, true),
                            C04PacketPlayerPosition(x, y, z, false)
                        )
                    }
                }

                "hop" -> {
                    thePlayer.motionY = 0.1
                    thePlayer.fallDistance = 0.1f
                    thePlayer.onGround = false
                }

                "tphop" -> {
                    sendPackets(
                        C04PacketPlayerPosition(x, y + 0.02, z, false),
                        C04PacketPlayerPosition(x, y + 0.01, z, false)
                    )
                    thePlayer.setPosition(x, y + 0.01, z)
                }

                "jump" -> thePlayer.motionY = 0.42
                "lowjump" -> thePlayer.motionY = 0.3425
                "custommotion" -> thePlayer.motionY = customMotionY.toDouble()
                "visual" -> thePlayer.onCriticalHit(entity)
                
                "intave" -> {
                    handleIntaveCriticals(x, y, z, entity, false)
                }
                
                "intavesilent" -> {
                    handleIntaveCriticals(x, y, z, entity, true)
                }
                
                "intaveadvanced" -> {
                    // Phương pháp nâng cao với timing manipulation
                    val currentTime = System.currentTimeMillis()
                    val timeOffset = (currentTime % 1000) / 1000.0
                    
                    sendPackets(
                        C04PacketPlayerPosition(x, y + 0.0010 + timeOffset * 0.0001, z, false),
                        C04PacketPlayerPosition(x, y + 0.0011 + timeOffset * 0.0001, z, false),
                        C04PacketPlayerPosition(x, y + 0.0012 + timeOffset * 0.0001, z, false),
                        C04PacketPlayerPosition(x, y, z, false)
                    )
                    
                    thePlayer.onCriticalHit(entity)
                }
            }

            msTimer.reset()
        }
    }

    /**
     * Xử lý criticals cho Intave anti-cheat
     */
    private fun handleIntaveCriticals(x: Double, y: Double, z: Double, entity: EntityLivingBase, silent: Boolean) {
        val packets = mutableListOf<C04PacketPlayerPosition>()
        
        // Tạo các packet với độ offset ngẫu nhiên nhỏ
        for (i in 1..intavePacketCount) {
            val offset = when (i) {
                1 -> 0.0011000000000000001 + random.nextDouble() * intaveRandomness
                2 -> 0.0011000000000000002 + random.nextDouble() * intaveRandomness
                3 -> 0.0011000000000000003 + random.nextDouble() * intaveRandomness
                4 -> 0.0011000000000000004 + random.nextDouble() * intaveRandomness
                else -> 0.0011000000000000005 + random.nextDouble() * intaveRandomness
            }
            
            packets.add(C04PacketPlayerPosition(x, y + offset, z, false))
        }
        
        // Thêm packet trở về vị trí ban đầu
        packets.add(C04PacketPlayerPosition(x, y, z, false))
        
        // Gửi packets
        sendPackets(*packets.toTypedArray())
        
        if (!silent) {
            mc.thePlayer.onCriticalHit(entity)
        }
    }

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is C03PacketPlayer) {
            when (mode.lowercase()) {
                "nogrond" -> packet.onGround = false
                "intave", "intavesilent", "intaveadvanced" -> {
                    // Randomize onGround value để bypass detection
                    if (random.nextBoolean()) {
                        packet.onGround = !packet.onGround
                    }
                }
            }
        }
    }

    override val tag
        get() = mode
}
