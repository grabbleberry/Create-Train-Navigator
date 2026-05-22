package myaddon.content.transportation.blocks;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public class WinchBlockEntity extends KineticBlockEntity {

    private double ropeLength = 20.0; // Current deployment length in blocks
    private UUID targetTrainId = null;  // Keeps track of the pulled train
    private BlockPos hookPosition = null; // Where the rope attaches to the train

    public WinchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();

        if (level.isClientSide) return;

        // 1. Adjust rope length based on Create's rotational speed (RPM)
        if (getSpeed() != 0) {
            // High RPM retracts or extends the rope faster
            double deltaLength = (getSpeed() / 256.0) * 0.05; 
            this.ropeLength = Math.max(1.0, this.ropeLength - deltaLength);
            setChanged();
        }

        // 2. If hooked to a train, calculate the physics tension loop
        if (targetTrainId != null && hookPosition != null) {
            WinchPhysicsBridge.applyTensionToTrain(level, getBlockPos(), hookPosition, targetTrainId, ropeLength);
        }
    }

  //next section

  package myaddon.content.transportation.physics;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public class WinchPhysicsBridge {

    public static void applyTensionToTrain(Level level, BlockPos winchPos, BlockPos hookPos, UUID trainId, double allowedLength) {
        // Find the active Create Train via its UUID from Create's global registration tracker
        Train train = Create.RAILWAYS.trains.get(trainId);
        if (train == null) return;

        Vec3 winchVec = Vec3.atCenterOf(winchPos);
        
        // Target the specific carriage that holds the hook
        Carriage carriage = train.carriages.get(0); 
        if (carriage.anyInWorldTileEntity() == null) return;
        
        // Convert the train's relative grid spot back into actual active 3D world coordinates
        Vec3 trainWorldVec = carriage.anyInWorldTileEntity().getLevelAnchor();

        double currentDistance = winchVec.distanceTo(trainWorldVec);

        if (currentDistance > allowedLength) {
            // 1. Calculate how much the rope has stretched
            double stretch = currentDistance - allowedLength;
            double ropeElasticity = 0.15; // The stiffness constant (k)
            double tensionForce = stretch * ropeElasticity;

            // 2. Determine the 3D directional vector of the pull
            Vec3 pullDirection = winchVec.subtract(trainWorldVec).normalize();

            // 3. Get the train's forward tracking vector along the steel rails
            Vec3 trainForwardLook = carriage.bogeys.getFirst().getLeadingSpacing().getLookDirection();

            // 4. Vector Math: Dot Product projects the 3D force onto the 1D rail direction
            double forceAlongTrack = pullDirection.dot(trainForwardLook);

            // 5. Inject the final calculated force straight into the Create train's physics acceleration variables!
            double massModifier = 0.02 / train.carriages.size(); // Heavier trains accelerate slower
            train.speed += forceAlongTrack * tensionForce * massModifier;
        }
    }
}

//next section

package myaddon.content.transportation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelLastEvent;

public class RopeRenderer {

    public static void renderRopes(RenderLevelLastEvent event) {
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        // Example static coordinates (In your real code, fetch these dynamically from a client-side cache)
        Vec3 winchPos = new Vec3(100, 70, 100);
        Vec3 trainPos = new Vec3(105, 71, 102);

        poseStack.pushPose();
        // Shift rendering matrix relative to the player's moving camera position
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer builder = buffer.getBuffer(RenderType.lineStrip());

        // Draw a brown rope line connecting the two active points
        // (For premium graphics, you can turn this into a multi-segmented catenary curve to simulate sag!)
        builder.vertex(poseStack.last().pose(), (float)winchPos.x, (float)winchPos.y, (float)winchPos.z)
               .color(78, 59, 39, 255).endVertex();
               
        builder.vertex(poseStack.last().pose(), (float)trainPos.x, (float)trainPos.y, (float)trainPos.z)
               .color(78, 59, 39, 255).endVertex();

        buffer.endBatch(RenderType.lineStrip());
        poseStack.popPose();
    }
}

//next section

package myaddon.content.transportation.blocks;

import myaddon.register.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class WinchBlock extends HorizontalKineticBlock {

    public WinchBlock(Properties properties) {
        super(properties);
        // Set the default facing direction when placed
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        // Appends the horizontal facing property to the block's state system
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Automatically faces the player when placed down
        return this.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Tells the Create Mod which way the kinetic shaft/gears are spinning.
     * In this setup, the drive axis aligns perfectly with the direction the block faces.
     */
    @Override
    public Direction.Axis getRotationalAxis(BlockState state) {
        return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis();
    }

    /**
     * Bridges this block definition to its companion WinchBlockEntity.
     */
    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return ModBlockEntities.WINCH.get();
    }

    /**
     * Tells Create if this block has an integrated shaft connection on a given face.
     * Allowing shafts to connect to the back or front makes it easy to power.
     */
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationalAxis(state);
    }
}
    // Getters and setters for hooking items to the winch...
}
