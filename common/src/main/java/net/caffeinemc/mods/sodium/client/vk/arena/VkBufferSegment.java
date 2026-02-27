package net.caffeinemc.mods.sodium.client.vk.arena;

import net.caffeinemc.mods.sodium.client.util.UInt32;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;

// TODO: fine-grained segment update notification to avoid re-writing the entire render data on small changes
public class VkBufferSegment implements SizedTreeMap.Sized {
    private AllocatorBase allocator;
    private RegionAllocatorHandle owner;
    private int ownerIndex;

    private int offset; /* Uint32 */
    private int length; /* Uint32 */

    private VkBufferSegment next;
    private VkBufferSegment prev;

    public VkBufferSegment(VkBufferArena allocator, RegionAllocatorHandle owner, int ownerIndex, long offset, long length) {
        this.allocator = allocator;
        this.owner = owner;
        this.ownerIndex = ownerIndex;
        this.offset = UInt32.downcast(offset);
        this.length = UInt32.downcast(length);
    }

    public static VkBufferSegment createFreeSegment(VkBufferArena allocator, long offset, long length) {
        return new VkBufferSegment(allocator, null, 0, offset, length);
    }

    /* Uint32 */
    protected long getEnd() {
        return this.getOffset() + this.getLength();
    }

    /* Uint32 */
    public long getOffset() {
        return UInt32.upcast(this.offset);
    }

    /* Uint32 */
    public long getLength() {
        return UInt32.upcast(this.length);
    }

    protected void setOffset(long offset /* Uint32 */) {
        this.offset = UInt32.downcast(offset);
    }

    protected void setLength(long length /* Uint32 */) {
        this.length = UInt32.downcast(length);
    }

    protected void setOwner(RegionAllocatorHandle owner, int ownerIndex) {
        this.owner = owner;
        this.ownerIndex = ownerIndex;
    }

    protected void notifyOwnerSegmentChanged(CommandList commands) {
        this.owner.notifySegmentChanged(commands, this.ownerIndex);
    }

    protected void setFree() {
        this.owner = null;
    }

    protected boolean isFree() {
        return this.owner == null;
    }

    protected void setNext(VkBufferSegment next) {
        this.next = next;
    }

    protected VkBufferSegment getNext() {
        return this.next;
    }

    protected VkBufferSegment getPrev() {
        return this.prev;
    }

    protected void setPrev(VkBufferSegment prev) {
        this.prev = prev;
    }

    public void delete() {
        this.allocator.free(this);
    }

    void setAllocator(AllocatorBase allocator) {
        this.allocator = allocator;
    }

    protected RegionAllocatorHandle getOwner() {
        return this.owner;
    }

    protected void mergeInto(VkBufferSegment entry) {
        this.setLength(this.getLength() + entry.getLength());
        this.setNext(entry.getNext());

        if (this.getNext() != null) {
            this.getNext().setPrev(this);
        }
    }

    @Override
    public long getSize() {
        return this.getLength();
    }

    @Override
    public long getIdentifier() {
        return this.getOffset();
    }
}
