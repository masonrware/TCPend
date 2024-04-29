SRC_DIR := src
BIN_DIR := src
JAVAC := javac
MAIN_SENDER := Sender
MAIN_RECEIVER := Receiver
MAIN_TCPEND := TCPend
CLASSPATH := $(BIN_DIR)

.PHONY: all clean

all: $(SRC_DIR)/$(MAIN_SENDER).class $(SRC_DIR)/$(MAIN_RECEIVER).class $(SRC_DIR)/$(MAIN_TCPEND).class

$(SRC_DIR)/$(MAIN_SENDER).class: $(SRC_DIR)/$(MAIN_SENDER).java
	$(JAVAC) -d $(BIN_DIR) $<

$(SRC_DIR)/$(MAIN_RECEIVER).class: $(SRC_DIR)/$(MAIN_RECEIVER).java
	$(JAVAC) -d $(BIN_DIR) $<

$(SRC_DIR)/$(MAIN_TCPEND).class: $(SRC_DIR)/$(MAIN_TCPEND).java
	$(JAVAC) -d $(BIN_DIR) -cp $(CLASSPATH) $<

clean:
	rm -rf $(SRC_DIR)/*.class
