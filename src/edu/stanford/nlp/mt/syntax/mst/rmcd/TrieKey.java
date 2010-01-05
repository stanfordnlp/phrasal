package mt.syntax.mst.rmcd;

/**
 * @author Michel Galley
 */
class TrieKey {

  TrieAlphabet alphabet;
  Trie root;
  Trie n;

  public TrieKey(TrieAlphabet alphabet) {
    this.alphabet = alphabet;
    this.root = alphabet.root();
    clear();
  }

  public TrieKey reset(Trie n) {
    this.n = n;
    return this;
  }

  public TrieKey clear() {
    n = root;
    return this;
  }

  public TrieKey stop() {
    alphabet.setFinal(n);
    return this;
  }

  public int id() {
    if(n == null)
      return -1;
    return n.id;
  }

  public TrieKey add(String str) {
    n = alphabet.lookup(n, str, true);
    return this;
  }
}
