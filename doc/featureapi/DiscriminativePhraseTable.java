public DiscriminativePhraseTable(String...args) {
  doSource = args.length > 0 ? Boolean.parseBoolean(args[0]) : true;
  doTarget = args.length > 1 ? Boolean.parseBoolean(args[1]) : true;
}
