package io.github.mattidragon.nodeflow.client.ui.screen;

import io.github.mattidragon.nodeflow.NodeFlow;
import io.github.mattidragon.nodeflow.client.ui.MessageToast;
import io.github.mattidragon.nodeflow.client.ui.widget.EditorAreaWidget;
import io.github.mattidragon.nodeflow.client.ui.widget.NodeWidget;
import io.github.mattidragon.nodeflow.graph.Connector;
import io.github.mattidragon.nodeflow.graph.Graph;
import io.github.mattidragon.nodeflow.graph.node.Node;
import io.github.mattidragon.nodeflow.graph.node.group.NodeGroup;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.*;

public class EditorScreen extends Screen {
    private static final Identifier DEFAULT_TEXTURE = NodeFlow.id("textures/gui/editor.png");
    public static final int TILE_SIZE = 16;
    public static final int BORDER_SIZE = 8;
    public static final int BORDER_OFFSET = 32;
    public static final int GRID_OFFSET = BORDER_OFFSET + BORDER_SIZE;

    public final Graph graph;
    public final Identifier texture;

    protected boolean isAddingNode = false;
    protected boolean isDeletingNode = false;
    protected @Nullable Connector<?> lastHoveredConnector = null;
    protected long lastHoveredTimestamp = 0;
    public @Nullable Connector<?> connectingConnector;

    public ButtonWidget backButton;
    protected final Map<NodeGroup, List<ButtonWidget>> nodeButtons = new HashMap<>();
    protected final List<ButtonWidget> groupButtons = new ArrayList<>();
    @Nullable
    protected NodeGroup activeGroup = null;

    public ButtonWidget plusButton;
    public ButtonWidget deleteButton;
    private AddNodesWidget addMenu;
    protected EditorAreaWidget area;

    public EditorScreen(Text title, Graph graph) {
        this(title, graph, DEFAULT_TEXTURE);
    }

    public EditorScreen(Text title, Graph graph, Identifier texture) {
        super(title);
        this.graph = graph;
        this.texture = texture;

        // Moved to right spot before usage, no need to reinit
        for (NodeGroup group : graph.env.groups()) {
            addGroup(graph, group);
        }
    }

    @Override
    public <T extends Element & Drawable & Selectable> T addDrawableChild(T drawableElement) {
        return super.addDrawableChild(drawableElement);
    }

    private void addGroup(Graph graph, NodeGroup group) {
        groupButtons.add(ButtonWidget.builder(group.getName(), button -> {
            activeGroup = group;
            updateAddButtons();
            updateVisibility();
        }).dimensions(0, 0, 100, 20).build());

        var buttons = new ArrayList<ButtonWidget>();
        for (var type : group.getTypes()) {
            buttons.add(ButtonWidget.builder(type.name(), button1 -> {
                toggleAddingMode();
                var node = type.generator().apply(graph);
                node.guiX = (int) area.modifyX(width / 2.0);
                node.guiY = (int) area.modifyY(height / 2.0);

                graph.addNode(node);
                var widget = new NodeWidget(node, this);
                area.add(widget);
                syncGraph();
            }).dimensions(0, 0, 100, 20).build());
        }
        nodeButtons.put(group, buttons);
    }

    @Override
    protected void init() {
        area = addDrawableChild(new EditorAreaWidget(GRID_OFFSET, GRID_OFFSET, getBoxWidth(), getBoxHeight(), this));
        area.children().clear();
        for (var node : graph.getNodes()) {
            var widget = new NodeWidget(node, this);
            area.add(widget);
        }

        plusButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> toggleAddingMode()).dimensions(GRID_OFFSET, BORDER_OFFSET - 20, 100, 20).build());
        deleteButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button1 -> toggleDeletingMode()).dimensions(GRID_OFFSET + 110, BORDER_OFFSET - 20, 100, 20).build());
        backButton = addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> {
            activeGroup = null;
            updateAddButtons();
            updateVisibility();
        }).dimensions(GRID_OFFSET + 110, BORDER_OFFSET - 20, 100, 20).build());

        addMenu = addDrawableChild(new AddNodesWidget(client, getBoxWidth(), getBoxHeight(), GRID_OFFSET + 10, height - GRID_OFFSET - 10));
        updateAddButtons();
        updateVisibility();
    }

    private void updateAddButtons() {
        var buttons = activeGroup == null ? groupButtons : nodeButtons.get(activeGroup);

        var entries = new ArrayList<AddNodesWidget.Entry>();
        var currentButtons = new ArrayList<ButtonWidget>();
        for (var button : buttons) {
            currentButtons.add(button);
            if (currentButtons.size() >= addMenu.getButtonCount()) {
                entries.add(new AddNodesWidget.Entry(currentButtons));
                currentButtons = new ArrayList<>();
            }
        }
        if (!currentButtons.isEmpty()) {
            entries.add(new AddNodesWidget.Entry(currentButtons));
        }
        addMenu.replaceEntries(entries);
        addMenu.setScrollAmount(0);
    }

    public void syncGraph() {}

    private void toggleDeletingMode() {
        isDeletingNode = !isDeletingNode;
        updateVisibility();
    }

    private void toggleAddingMode() {
        isAddingNode = !isAddingNode;
        activeGroup = null;
        updateAddButtons();
        updateVisibility();
    }

    private void updateVisibility() {
        area.children().forEach(node -> {
            node.active = !isAddingNode;
            //node.visible = !isAddingNode;
        });
        backButton.active = isAddingNode && activeGroup != null;
        backButton.visible = isAddingNode && activeGroup != null;
        addMenu.active = isAddingNode;
        deleteButton.active = !isAddingNode;
        deleteButton.visible = !isAddingNode;
        plusButton.active = !isDeletingNode;
        plusButton.visible = !isDeletingNode;
        //area.visible = !isAddingNode;
        area.active = !isAddingNode;
        deleteButton.setMessage(isDeletingNode ? ScreenTexts.CANCEL : Text.translatable("nodeflow.editor.button.delete_nodes"));
        plusButton.setMessage(isAddingNode ? ScreenTexts.CANCEL : Text.translatable("nodeflow.editor.button.add_node"));
    }

    public void removeNode(NodeWidget node) {
        graph.removeNode(node.node.id);
        area.children().remove(node);
        area.remove(node);
        syncGraph();
    }

    private void tryFindConnection(double mouseX, double mouseY) {
        var row = findConnectorAt(mouseX, mouseY);
        if (row == null) return;
        if (connectingConnector == null) return;
        if (row.equals(connectingConnector)) {
            graph.removeConnections(connectingConnector);
            return;
        }

        if (connectingConnector.isOutput() == row.isOutput()) {
            if (row.isOutput()) {
                showToast(Text.translatable("nodeflow.editor.toast.two_outputs").formatted(Formatting.RED));
            } else {
                showToast(Text.translatable("nodeflow.editor.toast.two_inputs").formatted(Formatting.RED));
            }
            return;
        }

        if (connectingConnector.type() != row.type()) {
            showToast(Text.translatable("nodeflow.editor.toast.different_type").formatted(Formatting.RED));
            return;
        }

        if (!row.type().splittable() || !row.isOutput()) {
            graph.removeConnections(row);
        }
        graph.addConnection(connectingConnector, row);

        var stack = new ArrayDeque<Connector<?>>();
        var searchTarget = connectingConnector.isOutput() ? connectingConnector : row;
        var searchStarter = connectingConnector.isOutput() ? row : connectingConnector;

        Arrays.stream(searchStarter.parent().getOutputs())
                .map(graph::getConnections)
                .flatMap(Set::stream)
                .map(connection -> connection.getTargetConnector(graph))
                .filter(Objects::nonNull)
                .forEach(stack::push);

        while (!stack.isEmpty()) {
            var element = stack.pop();
            if (element.equals(searchTarget) || element.equals(searchStarter)) {
                showToast(Text.translatable("nodeflow.editor.toast.recursion").formatted(Formatting.RED));
                graph.removeConnections(connectingConnector);
                return;
            }

            Arrays.stream(element.parent().getOutputs())
                    .map(graph::getConnections)
                    .flatMap(Set::stream)
                    .map(connection -> connection.getTargetConnector(graph))
                    .filter(Objects::nonNull)
                    .forEach(stack::push);
        }

        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1f));

    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (connectingConnector != null && button == 0) {
            if (!connectingConnector.type().splittable() || !connectingConnector.isOutput())
                graph.removeConnections(connectingConnector);

            tryFindConnection(mouseX, mouseY);

            area.children().forEach(NodeWidget::updateTooltip);

            connectingConnector = null;
        }
//        setFocused(null);
        // Sync node movement and connector changes
        syncGraph();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (super.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }
        if (isAddingNode) {
            addMenu.mouseScrolled(mouseX, mouseY, amount);
            return true;
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (isAddingNode) return;
        var connector = findConnectorAt(mouseX, mouseY);
        if (connector == null || client == null) return;

        long time = client.world == null ? 0 : client.world.getTime();
        if (!connector.equals(lastHoveredConnector) || time - lastHoveredTimestamp > 10) {
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.7f));
        }
        lastHoveredConnector = connector;
        lastHoveredTimestamp = time;
    }

    public NodeWidget findWidget(Node node) {
        for (var widget : area.children()) {
            if (widget.node == node)
                return widget;
        }
        return null;
    }

    public NodeWidget.Segment findSegment(Connector<?> connector) {
        for (var segment : findWidget(connector.parent()).calculateSegments()) {
            if (segment.connector.equals(connector))
                return segment;
        }
        return null;
    }

    @Nullable
    private Connector<?> findConnectorAt(double mouseX, double mouseY) {
        for (var node : area.children()) {
            for (var segment : node.calculateSegments()) {
                if (segment.hasConnectorAt(area.modifyX(mouseX), area.modifyY(mouseY))) {
                    return segment.connector;
                }
            }
        }
        return null;
    }

    @Nullable
    public NodeWidget.Segment findSegmentAt(double mouseX, double mouseY) {
        for (var node : area.children()) {
            for (var segment : node.calculateSegments()) {
                if (segment.hasConnectorAt(area.modifyX(mouseX), area.modifyY(mouseY))) {
                    return segment;
                }
            }
        }
        return null;
    }

    public int getBoxWidth() {
        return (this.width - GRID_OFFSET * 2) / TILE_SIZE * TILE_SIZE;
    }

    public int getBoxHeight() {
        return (this.height - GRID_OFFSET * 2) / TILE_SIZE * TILE_SIZE;
    }

    public void showToast(Text message) {
        MinecraftClient.getInstance().getToastManager().add(new MessageToast(message));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        super.close();
        if (client != null && client.player != null) {
            client.player.closeHandledScreen();
        }
    }

    @Override
    public void renderBackground(DrawContext context) {
        super.renderBackground(context);
        renderArea(context);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        for (var node : area.children())
            node.updateTooltip();

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderArea(DrawContext context) {
        var rows = (this.height - GRID_OFFSET * 2) / TILE_SIZE;
        var columns = (this.width - GRID_OFFSET * 2) / TILE_SIZE;
        int boxHeight = getBoxHeight();
        int boxWidth = getBoxWidth();

        // DrawableHelper.drawTexture is too slow because it uses one draw call per call. We only need it at the end.
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        var matrix = context.getMatrices().peek().getPositionMatrix();
        var vOffset = area.isFocused() ? 32 : 0;

        // Draws the main grid
        for (int x = 0; x < columns; x++) {
            for (int y = 0; y < rows; y++) {
                addTexturedQuad(matrix, GRID_OFFSET + TILE_SIZE * x, GRID_OFFSET + TILE_SIZE * y, 8, 8 + vOffset, 16, 16);
            }
        }

        // Draws top and bottoms
        for (int x = 0; x < columns; x++) {
            addTexturedQuad(matrix, GRID_OFFSET + TILE_SIZE * x, BORDER_OFFSET, 8, vOffset, 16, 8);
            addTexturedQuad(matrix, GRID_OFFSET + TILE_SIZE * x, GRID_OFFSET + boxHeight, 8, 24 + vOffset, 16, 8);
        }

        // Draws sides
        for (int y = 0; y < rows; y++) {
            addTexturedQuad(matrix, BORDER_OFFSET, GRID_OFFSET + TILE_SIZE * y, 0, 8 + vOffset, 8, 16);
            addTexturedQuad(matrix, GRID_OFFSET + boxWidth, GRID_OFFSET + TILE_SIZE * y, 24, 8 + vOffset, 8, 16);
        }

        // Draws corners
        addTexturedQuad(matrix, BORDER_OFFSET, BORDER_OFFSET, 0, vOffset, 8, 8);
        addTexturedQuad(matrix, BORDER_OFFSET, GRID_OFFSET + boxHeight, 0, 24 + vOffset, 8, 8);
        addTexturedQuad(matrix, GRID_OFFSET + boxWidth, BORDER_OFFSET , 24, vOffset, 8, 8);
        addTexturedQuad(matrix, GRID_OFFSET + boxWidth, GRID_OFFSET + boxHeight, 24, 24 + vOffset, 8, 8);

        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    public static void addTexturedQuad(Matrix4f matrix, int x1, int y1, int u, int v, int width, int height) {
        int x2 = x1 + width;
        int y2 = y1 + height;
        float u1 = u / 256f;
        float u2 = (u + width) / 256f;
        float v1 = v / 256f;
        float v2 = (v + height) / 256f;

        BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();

        bufferBuilder.vertex(matrix, x1, y2, 0).texture(u1, v2).next();
        bufferBuilder.vertex(matrix, x2, y2, 0).texture(u2, v2).next();
        bufferBuilder.vertex(matrix, x2, y1, 0).texture(u2, v1).next();
        bufferBuilder.vertex(matrix, x1, y1, 0).texture(u1, v1).next();
    }

    public boolean isDeletingNode() {
        return isDeletingNode;
    }

    public EditorAreaWidget getArea() {
        return area;
    }

    @Override
    public void setFocused(@Nullable Element focused) {
        if (focused == getFocused()) return;
        super.setFocused(focused);
    }

    private static class AddNodesWidget extends ElementListWidget<AddNodesWidget.Entry> {
        public boolean active = true;

        public AddNodesWidget(MinecraftClient client, int width, int height, int top, int bottom) {
            super(client, width, height, top, bottom, 30);
            setRenderBackground(false);
            setRenderHorizontalShadows(false);
            centerListVertically = true;
            left = GRID_OFFSET;
        }

        @Override
        public void replaceEntries(Collection<Entry> newEntries) {
            super.replaceEntries(newEntries);
        }

        @Override
        public int addEntry(Entry entry) {
            return super.addEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return getButtonCount() * 110 - 10;
        }

        public int getButtonCount() {
            return (width - 50) / 110;
        }

        @Override
        protected int getScrollbarPositionX() {
            return (width / 2) + (getRowWidth() / 2) + GRID_OFFSET + 10;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!active) return false;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (!active) return;
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        protected void renderList(DrawContext context, int mouseX, int mouseY, float delta) {
            context.enableScissor(0, top, width + GRID_OFFSET, bottom);
            super.renderList(context, mouseX, mouseY, delta);
            context.disableScissor();
        }

        @Nullable
        @Override
        public GuiNavigationPath getNavigationPath(GuiNavigation navigation) {
            if (!active) return null;

            return super.getNavigationPath(navigation);
        }

        private static class Entry extends ElementListWidget.Entry<Entry> {
            private final List<ButtonWidget> buttons;

            public Entry(List<ButtonWidget> buttons) {
                super();
                this.buttons = buttons;
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return buttons;
            }

            @Override
            public List<? extends Element> children() {
                return buttons;
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
                for (int i = 0; i < buttons.size(); i++) {
                    var button = buttons.get(i);
                    button.setY(y);
                    button.setX(x + i * 110);
                    button.render(context, mouseX, mouseY, delta);
                }
            }
        }
    }
}
