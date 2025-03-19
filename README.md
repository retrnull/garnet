# Garnet

A fork of Amethyst with Monero tipping support

## Functionality

A Monero spend key is created based on the Nostr private key. The app has its own wallet which needs to be funded in order to tip posts.

A "cryptocurrency_addresses" field is added to kind 0 events, which maps cryptocurrency names (e.g. "monero") to addresses.

Every new post includes one or more "monero" tags, which functions very similarly to the "zap" tag (see NIP-57) and is used both for normal tips and split tips.

There are three tip types that the user can choose from:

1. Private: No traces
2. Anonymous: Everybody can see the transaction and message but not the sender
3. Public: Everybody can see the transaction, the message and the sender

For anonymous and public tips, a tip event (kind 1814) is transmitted to the user's relays, whose content is a string representation of a JSON object that specifies the txid, the message, and a mapping from transaction proofs to addresses. The tip event also includes the standard tags to refer to events and user profiles, which are also used for verifying the event.

For every tip a user receives, a counter is displayed beside the tip icon in the tipped note, as well as in the user profile's "Tips" tab, similarly to zaps.

The user can transfer the received tips at any moment to an external wallet.

## Download and install

See [releases](https://github.com/retrnull/garnet/releases)

## Building

Make sure to have the following pre-requisites installed:
1. Java 17+
2. Android Studio
3. Android 8.0+ Phone or Emulation setup
4. Docker

Clone this repository and import it into Android Studio
```bash
git clone https://github.com/retrnull/garnet.git
```

Clone the Monero fork repository and switch to the correct branch
```bash
git clone https://github.com/retrnull/monero.git
cd monero
git checkout release-v0.18.3.3-garnet
```

Update submodules
```bash
git submodule update --init --force
```

Create a symbolic link to the monero repository in garnet's external-libs directory
```bash
ln -s ~/monero ~/garnet/external-libs/monero
```

Start docker and run `make`. Then, build the APK in Android Studio.

## Donations

Monero address: 8A7LStrTLTTfGMBmLYCfBC9BUtVZuLZA3hdQe6TnKBGzA3SKzXz8innAgjNAZn1o3Ed3mpSCSJWQH2x1Y1Us1k9BBmHx4AA
