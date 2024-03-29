package com.simedge.protocols;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class PeerMessage {

    /**
     * Enum describing message types
     */
    public enum MessageType {
        EXECUTE((byte) 1),
        RESULT((byte) 2),
        PING((byte) 0);

        private final byte id;

        private MessageType(byte id) {
            this.id = id;
        }

        public static MessageType processByte(byte b) {
            switch (b) {
                case (byte) 0:
                    return MessageType.PING;
                case (byte) 1:
                    return MessageType.EXECUTE;
                case (byte) 2:
                    return MessageType.RESULT;
                default:
                    return MessageType.PING;
            }
        }
    }

    /**
     * Data type enum
     */
    public enum DataType {
        BYTE((byte) 1),
        INT((byte) 2),
        LONG((byte) 3),
        FLOAT((byte) 4),
        DOUBLE((byte) 5),
        CHAR((byte) 6),
        UNKNOWN((byte) 0);

        private final byte id;

        private DataType(byte id) {
            this.id = id;
        }

        private static DataType processByte(byte b) {
            switch (b) {
                case (byte) 1:
                    return DataType.BYTE;
                case (byte) 2:
                    return DataType.INT;
                case (byte) 3:
                    return DataType.LONG;
                case (byte) 4:
                    return DataType.FLOAT;
                case (byte) 5:
                    return DataType.DOUBLE;
                case (byte) 6:
                    return DataType.CHAR;
                default:
                    return DataType.UNKNOWN;
            }
        }

        public int getDataTypeSize() {
            switch (this) {
                case BYTE:
                    return 1;
                case INT:
                    return 4;
                case LONG:
                    return 8;
                case FLOAT:
                    return 4;
                case DOUBLE:
                    return 8;
                case CHAR:
                    return 2;
                default:
                    return 1;
            }
        }
    }

    static final int hashlength = 20;
    static final int longLength = 8;
    static final int messageTypeLength = 1;
    static final int dataTypeLength = 1;
    static int messageCounter = 0;

    public long messageNumber = messageCounter + 1;
    public MessageType messageType;
    DataType dataTye;
    ByteBuffer data;
    byte[] modelHash;
    int inputNameLength;
    String inputName;
    int indicesLength;
    int[] indices;
    public long onnxTime;

    /**
     * Constructor for creating a PeerMessage instance from byte package that is
     * received
     * 
     * @param packet Byte package received
     */
    public PeerMessage(byte[] packet) {
        data = ByteBuffer.allocate(packet.length);
        // data = ByteBuffer.allocateDirect(packet.length);

        data.put(packet);

        data.position(0);

        this.messageNumber = data.getLong();

        this.messageType = MessageType.processByte(data.get());

        if (messageType == MessageType.EXECUTE) {
            this.dataTye = DataType.processByte(data.get());
            modelHash = new byte[hashlength];
            for (int i = 0; i < modelHash.length; i++) {
                modelHash[i] = data.get();
            }

            inputNameLength = data.getInt();
            byte[] inputNameBytes = new byte[inputNameLength];
            for (int i = 0; i < inputNameBytes.length; i++) {
                inputNameBytes[i] = data.get();
            }
            inputName = new String(inputNameBytes, StandardCharsets.UTF_8);

            indicesLength = data.getInt();
            indices = new int[indicesLength / DataType.INT.getDataTypeSize()];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = data.getInt();
            }

        } else if (messageType == MessageType.RESULT) {
            this.onnxTime = data.getLong();
            System.out.println("Result with " + data.remaining() + " bytes received");
        } else if (messageType == MessageType.PING) {
            System.out.println("Ping Received");
            modelHash = new byte[hashlength];
            for (int i = 0; i < modelHash.length; i++) {
                modelHash[i] = data.get();
            }
        }

    }

    /**
     * Construct for execute message
     * 
     * @param messageType Message type
     * @param dataType    Data type
     * @param data        byte array of data
     * @param modelHash   byte array of model hash
     * @param inputName   String input name of model
     * @param indices     Reduction indicies to reduce model
     */
    public PeerMessage(MessageType messageType, DataType dataType, byte[] data, byte[] modelHash,
            String inputName, int[] indices) {
        this.messageNumber = messageCounter;
        messageCounter++;
        this.messageType = messageType;
        this.dataTye = dataType;
        this.data = ByteBuffer.allocate(data.length);
        this.data.put(data);
        this.modelHash = modelHash;
        this.inputName = inputName;
        this.inputNameLength = this.inputName.getBytes(StandardCharsets.UTF_8).length;
        this.indicesLength = indices.length * DataType.INT.getDataTypeSize();
        this.indices = indices;
    }

    /**
     * Constructor for a Result message
     * 
     * @param data          result data as byte buffer
     * @param messageNumber Message number
     * @param onnxTime      execution time
     */
    public PeerMessage(ByteBuffer data, long messageNumber, long onnxTime) {
        this.messageNumber = messageNumber;
        this.messageType = MessageType.RESULT;
        this.data = data;
        this.onnxTime = onnxTime;
    }

    /**
     * Construct a PING message
     * 
     * @param messageNumber message number
     * @param modelHash     model hash
     */
    public PeerMessage(long messageNumber, byte[] modelHash) {
        this.messageNumber = messageNumber;
        this.messageType = MessageType.PING;
        this.modelHash = modelHash;
    }

    /**
     * Returns the peer message serialized as byte array for sending over drasyl
     * node
     * 
     * @return byte array representation of peer message
     */
    public byte[] getMessageBytes() {
        if (this.messageType == MessageType.EXECUTE) {
            ByteBuffer byteBuffer = ByteBuffer
                    .allocate(longLength + messageTypeLength + dataTypeLength + hashlength
                            + DataType.INT.getDataTypeSize() + inputNameLength
                            + DataType.INT.getDataTypeSize() + indicesLength + data.limit());
            byteBuffer.putLong(messageNumber);
            byteBuffer.put(messageType.id);
            byteBuffer.put(dataTye.id);
            byteBuffer.put(modelHash);
            byteBuffer.putInt(inputNameLength);
            byteBuffer.put(inputName.getBytes(StandardCharsets.UTF_8));
            // adding indicies
            byteBuffer.putInt(indicesLength);

            for (int i : indices) {
                byteBuffer.putInt(i);
            }
            data.position(0);
            byteBuffer.put(data);
            return byteBuffer.array();
        } else if (this.messageType == MessageType.RESULT) {
            ByteBuffer byteBuffer = ByteBuffer
                    .allocate(longLength + messageTypeLength + longLength + data.limit());
            byteBuffer.putLong(messageNumber);
            byteBuffer.put(messageType.id);
            byteBuffer.putLong(onnxTime);
            data.position(0);
            byteBuffer.put(data);
            return byteBuffer.array();
        } else if (this.messageType == MessageType.PING) {
            ByteBuffer byteBuffer = ByteBuffer
                    .allocate(longLength + messageTypeLength + hashlength);
            byteBuffer.putLong(messageNumber);
            byteBuffer.put(MessageType.PING.id);
            byteBuffer.put(modelHash);
            return byteBuffer.array();
        } else {
            return ByteBuffer.allocate(1).array();
        }

    }

}
