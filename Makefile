SRC_DIR := src
BIN_DIR := bin
JAVAC := javac
MAIN_SENDER := Sender
MAIN_RECEIVER := Receiver

.PHONY: all clean

all: $(BIN_DIR)/$(MAIN_SENDER).class $(BIN_DIR)/$(MAIN_RECEIVER).class

$(BIN_DIR)/$(MAIN_SENDER).class: $(SRC_DIR)/$(MAIN_SENDER).java
	$(JAVAC) -d $(BIN_DIR) $<

$(BIN_DIR)/$(MAIN_RECEIVER).class: $(SRC_DIR)/$(MAIN_RECEIVER).java
	$(JAVAC) -d $(BIN_DIR) $<

clean:
	rm -rf $(BIN_DIR)/*
